package br.com.crispimcampos.gestao.contas;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestao de usuarios e OABs (item 1). Quem chama (controller) ja garantiu o perfil Socia;
 * as regras de dominio ficam aqui, para valerem para qualquer entrada.
 */
@Service
public class UsuarioService {

	static final int SENHA_MINIMA = 12;
	private static final Set<Perfil> PERFIS_COM_OAB = Set.of(Perfil.SOCIA, Perfil.ADVOGADO);

	private final UsuarioRepository usuarios;
	private final OabRepository oabs;
	private final PasswordEncoder encoder;
	private final AuditoriaService auditoria;
	private final ApplicationEventPublisher eventos;

	UsuarioService(UsuarioRepository usuarios, OabRepository oabs, PasswordEncoder encoder,
			AuditoriaService auditoria, ApplicationEventPublisher eventos) {
		this.usuarios = usuarios;
		this.oabs = oabs;
		this.encoder = encoder;
		this.auditoria = auditoria;
		this.eventos = eventos;
	}

	@Transactional(readOnly = true)
	public List<UsuarioDto> listar() {
		return usuarios.findAll().stream().map(this::paraDto).toList();
	}

	@Transactional(readOnly = true)
	public UsuarioDto buscar(Long id) {
		return paraDto(obter(id));
	}

	@Transactional
	public UsuarioDto criar(Long atorId, String nome, String email, String senha, Perfil perfil, Long supervisorId) {
		validarSenha(senha);
		String emailNormalizado = email.trim().toLowerCase();
		if (usuarios.findByEmail(emailNormalizado).isPresent()) {
			throw conflito("Ja existe usuario com este e-mail");
		}
		Usuario u = new Usuario(nome.trim(), emailNormalizado, encoder.encode(senha), perfil);
		definirSupervisor(u, perfil, supervisorId);
		u = usuarios.save(u);
		auditoria.registrar(atorId, "USUARIO_CRIADO", "usuario", String.valueOf(u.getId()), "perfil=" + perfil);
		eventos.publishEvent(new UsuarioCriado(u.getId(), perfil));
		return paraDto(u);
	}

	@Transactional
	public UsuarioDto atualizar(Long atorId, Long id, String nome, String email, Perfil perfil, Long supervisorId) {
		Usuario u = obter(id);
		String emailNormalizado = email.trim().toLowerCase();
		usuarios.findByEmail(emailNormalizado).filter(outro -> !outro.getId().equals(id)).ifPresent(outro -> {
			throw conflito("Ja existe usuario com este e-mail");
		});
		if (u.getPerfil() != perfil) {
			if (id.equals(atorId)) {
				throw regra("Voce nao pode alterar o proprio perfil");
			}
			if (u.getPerfil() == Perfil.SOCIA && u.isAtivo() && usuarios.countByPerfilAndAtivoTrue(Perfil.SOCIA) <= 1) {
				throw regra("Deve haver ao menos uma socia ativa");
			}
			if (!PERFIS_COM_OAB.contains(perfil) && oabs.findByUsuarioIdOrderByPrincipalDescIdAsc(id).stream().anyMatch(Oab::isAtiva)) {
				throw regra("Desative as OABs antes de mudar para um perfil sem OAB");
			}
			if (perfil != Perfil.ADVOGADO && !usuarios.findBySupervisorIdAndAtivoTrue(id).isEmpty()) {
				throw regra("Usuario supervisiona estagiarios ativos");
			}
		}
		u.atualizar(nome.trim(), emailNormalizado, perfil);
		definirSupervisor(u, perfil, supervisorId);
		auditoria.registrar(atorId, "USUARIO_ATUALIZADO", "usuario", String.valueOf(id), "perfil=" + perfil);
		return paraDto(u);
	}

	@Transactional
	public void desativar(Long atorId, Long id) {
		Usuario u = obter(id);
		if (id.equals(atorId)) {
			throw regra("Voce nao pode desativar o proprio usuario");
		}
		if (u.getPerfil() == Perfil.SOCIA && usuarios.countByPerfilAndAtivoTrue(Perfil.SOCIA) <= 1) {
			throw regra("Deve haver ao menos uma socia ativa");
		}
		if (!usuarios.findBySupervisorIdAndAtivoTrue(id).isEmpty()) {
			throw regra("Usuario supervisiona estagiarios ativos; reatribua o supervisor antes");
		}
		u.desativar();
		auditoria.registrar(atorId, "USUARIO_DESATIVADO", "usuario", String.valueOf(id), null);
	}

	@Transactional
	public void reativar(Long atorId, Long id) {
		obter(id).reativar();
		auditoria.registrar(atorId, "USUARIO_REATIVADO", "usuario", String.valueOf(id), null);
	}

	@Transactional
	public void redefinirSenha(Long atorId, Long id, String novaSenha) {
		validarSenha(novaSenha);
		obter(id).trocarSenha(encoder.encode(novaSenha));
		auditoria.registrar(atorId, "SENHA_REDEFINIDA", "usuario", String.valueOf(id), null);
	}

	@Transactional
	public OabDto adicionarOab(Long atorId, Long usuarioId, String numero, String uf, boolean principal) {
		Usuario u = obter(usuarioId);
		if (!PERFIS_COM_OAB.contains(u.getPerfil())) {
			throw regra("Somente socias e advogados possuem OAB");
		}
		Oab nova = new Oab(u, numero, uf, false);
		if (nova.getNumero().isEmpty() || !nova.getUf().matches("[A-Z]{2}")) {
			throw regra("Numero ou UF da OAB invalidos");
		}
		if (oabs.existsByNumeroAndUf(nova.getNumero(), nova.getUf())) {
			throw conflito("Esta OAB ja esta cadastrada");
		}
		List<Oab> existentes = oabs.findByUsuarioIdOrderByPrincipalDescIdAsc(usuarioId);
		boolean ficaPrincipal = principal || existentes.stream().noneMatch(Oab::isPrincipal);
		if (ficaPrincipal) {
			existentes.forEach(o -> o.definirPrincipal(false));
			oabs.flush();
			nova.definirPrincipal(true);
		}
		nova = oabs.save(nova);
		auditoria.registrar(atorId, "OAB_ADICIONADA", "oab", String.valueOf(nova.getId()),
				"usuario=" + usuarioId + " uf=" + nova.getUf());
		eventos.publishEvent(new OabAtivada(nova.getId(), usuarioId, nova.getNumero(), nova.getUf()));
		return OabDto.de(nova);
	}

	@Transactional
	public void desativarOab(Long atorId, Long oabId) {
		Oab o = obterOab(oabId);
		o.desativar();
		auditoria.registrar(atorId, "OAB_DESATIVADA", "oab", String.valueOf(oabId), null);
	}

	@Transactional
	public void reativarOab(Long atorId, Long oabId) {
		Oab o = obterOab(oabId);
		o.ativar();
		auditoria.registrar(atorId, "OAB_REATIVADA", "oab", String.valueOf(oabId), null);
		eventos.publishEvent(new OabAtivada(o.getId(), o.getUsuario().getId(), o.getNumero(), o.getUf()));
	}

	@Transactional
	public void tornarPrincipal(Long atorId, Long oabId) {
		Oab o = obterOab(oabId);
		if (!o.isAtiva()) {
			throw regra("Reative a OAB antes de torna-la principal");
		}
		oabs.findByUsuarioIdOrderByPrincipalDescIdAsc(o.getUsuario().getId()).forEach(x -> x.definirPrincipal(false));
		oabs.flush();
		o.definirPrincipal(true);
		auditoria.registrar(atorId, "OAB_PRINCIPAL", "oab", String.valueOf(oabId), null);
	}

	private void definirSupervisor(Usuario u, Perfil perfil, Long supervisorId) {
		if (perfil != Perfil.ESTAGIARIO) {
			if (supervisorId != null) {
				throw regra("Somente estagiario tem supervisor");
			}
			return;
		}
		if (supervisorId == null) {
			throw regra("Estagiario exige um advogado supervisor");
		}
		Usuario sup = obter(supervisorId);
		if (sup.getPerfil() != Perfil.ADVOGADO || !sup.isAtivo()) {
			throw regra("O supervisor deve ser um advogado ativo");
		}
		u.vincularSupervisor(sup);
	}

	private void validarSenha(String senha) {
		if (senha == null || senha.length() < SENHA_MINIMA) {
			throw regra("A senha deve ter ao menos " + SENHA_MINIMA + " caracteres");
		}
	}

	private Usuario obter(Long id) {
		return usuarios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado"));
	}

	private Oab obterOab(Long id) {
		return oabs.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("OAB nao encontrada"));
	}

	private UsuarioDto paraDto(Usuario u) {
		List<OabDto> lista = oabs.findByUsuarioIdOrderByPrincipalDescIdAsc(u.getId()).stream().map(OabDto::de).toList();
		return new UsuarioDto(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo(),
				u.getSupervisor() == null ? null : u.getSupervisor().getId(), lista);
	}

	private static RegraDeContaException regra(String m) {
		return new RegraDeContaException(m, false);
	}

	private static RegraDeContaException conflito(String m) {
		return new RegraDeContaException(m, true);
	}
}
