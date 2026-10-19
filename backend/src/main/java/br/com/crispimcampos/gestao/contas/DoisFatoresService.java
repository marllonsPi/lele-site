package br.com.crispimcampos.gestao.contas;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segundo fator opcional por usuario (TOTP). Cadastro em duas etapas: iniciar (gera segredo) e confirmar (prova que o
 * aplicativo esta sincronizado); so entao passa a ser exigido no login. Desativar tambem exige um codigo valido.
 * Se o 2FA sera obrigatorio para socias e decisao pendente (docs/05, item 14).
 */
@Service
public class DoisFatoresService {

	static final String EMISSOR = "Crispim & Campos";

	public record Cadastro(String segredo, String uri) {
	}

	private final UsuarioRepository usuarios;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	DoisFatoresService(UsuarioRepository usuarios, AuditoriaService auditoria, Clock relogio) {
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public boolean ativo(Long usuarioId) {
		return obter(usuarioId).isTotpAtivo();
	}

	@Transactional
	public Cadastro iniciar(Long usuarioId) {
		Usuario u = obter(usuarioId);
		if (u.isTotpAtivo()) {
			throw new IllegalStateException("O segundo fator ja esta ativo; desative-o antes de cadastrar outro");
		}
		String segredo = Totp.novoSegredo();
		u.iniciarDoisFatores(segredo);
		auditoria.registrar(usuarioId, "2FA_INICIADO", "usuario", String.valueOf(usuarioId), null);
		return new Cadastro(segredo, Totp.uri(EMISSOR, u.getEmail(), segredo));
	}

	@Transactional
	public void confirmar(Long usuarioId, String codigo) {
		Usuario u = obter(usuarioId);
		if (u.getTotpSecret() == null || !Totp.valido(u.getTotpSecret(), codigo, relogio.instant())) {
			throw new IllegalArgumentException("Codigo invalido");
		}
		u.ativarDoisFatores();
		auditoria.registrar(usuarioId, "2FA_ATIVADO", "usuario", String.valueOf(usuarioId), null);
	}

	@Transactional
	public void desativar(Long usuarioId, String codigo) {
		Usuario u = obter(usuarioId);
		if (!u.isTotpAtivo() || !Totp.valido(u.getTotpSecret(), codigo, relogio.instant())) {
			throw new IllegalArgumentException("Codigo invalido ou segundo fator inativo");
		}
		u.desativarDoisFatores();
		auditoria.registrar(usuarioId, "2FA_DESATIVADO", "usuario", String.valueOf(usuarioId), null);
	}

	/** Usado no login: este e-mail exige codigo? */
	@Transactional(readOnly = true)
	public boolean exigeCodigo(String email) {
		return usuarios.findByEmail(email.trim().toLowerCase()).map(Usuario::isTotpAtivo).orElse(false);
	}

	@Transactional(readOnly = true)
	public boolean codigoValido(String email, String codigo) {
		return usuarios.findByEmail(email.trim().toLowerCase()).filter(Usuario::isTotpAtivo)
				.map(u -> Totp.valido(u.getTotpSecret(), codigo, relogio.instant())).orElse(false);
	}

	private Usuario obter(Long id) {
		return usuarios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuario nao encontrado"));
	}
}
