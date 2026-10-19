package br.com.crispimcampos.gestao.acesso;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioCriado;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edicao da matriz de visibilidade pelas socias (item 2) e regras iniciais de cada novo usuario.
 * Padrao por perfil (a socia ajusta depois, sem alterar o sistema):
 * socia ve todos os advogados/socias e a si; advogado ve so o proprio; assistente comeca sem acesso
 * (a socia libera os titulares, ex.: Luana nos titulares Leticia, Lorraynny e Andressa); estagiario nao usa a matriz.
 */
@Service
public class MatrizVisibilidadeService {

	private final RegraVisibilidadeRepository regras;
	private final UsuarioService usuarios;
	private final AuditoriaService auditoria;

	MatrizVisibilidadeService(RegraVisibilidadeRepository regras, UsuarioService usuarios, AuditoriaService auditoria) {
		this.regras = regras;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<ParVisibilidade> listar() {
		return regras.findAll().stream()
				.map(r -> new ParVisibilidade(r.getObservadorId(), r.getTitularId(), r.getEscopo())).toList();
	}

	/** Substitui as regras de um observador pelas informadas (a tela envia a linha inteira da matriz). */
	@Transactional
	public void definirLinha(Long atorId, Long observadorId, Set<ParVisibilidade> novas) {
		UsuarioDto observador = usuarios.buscar(observadorId);
		if (observador.perfil() == Perfil.ESTAGIARIO && !novas.isEmpty()) {
			throw new IllegalArgumentException("Estagiario nao usa a matriz: ve apenas o que lhe for atribuido");
		}
		Set<Long> titularesValidos = new HashSet<>();
		usuarios.listar().forEach(u -> titularesValidos.add(u.id()));
		for (ParVisibilidade p : novas) {
			if (!p.observadorId().equals(observadorId) || !titularesValidos.contains(p.titularId())) {
				throw new IllegalArgumentException("Regra invalida para o observador informado");
			}
		}
		regras.deleteAll(regras.findByObservadorId(observadorId));
		regras.flush();
		novas.forEach(p -> regras.save(new RegraVisibilidade(p.observadorId(), p.titularId(), p.escopo())));
		auditoria.registrar(atorId, "VISIBILIDADE_ALTERADA", "usuario", String.valueOf(observadorId),
				"regras=" + novas.size());
	}

	@EventListener
	@Transactional
	void aoCriarUsuario(UsuarioCriado evento) {
		Long novo = evento.id();
		Perfil perfil = evento.perfil();
		if (perfil == Perfil.ESTAGIARIO || perfil == Perfil.ASSISTENTE) {
			return;
		}
		for (Escopo e : EnumSet.allOf(Escopo.class)) {
			conceder(novo, novo, e);
		}
		for (UsuarioDto outro : usuarios.listar()) {
			if (outro.id().equals(novo) || !outro.ativo()) {
				continue;
			}
			for (Escopo e : EnumSet.allOf(Escopo.class)) {
				if (outro.perfil() == Perfil.SOCIA) {
					conceder(outro.id(), novo, e);
				}
				if (perfil == Perfil.SOCIA && (outro.perfil() == Perfil.SOCIA || outro.perfil() == Perfil.ADVOGADO)) {
					conceder(novo, outro.id(), e);
				}
			}
		}
	}

	private void conceder(Long observador, Long titular, Escopo escopo) {
		regras.save(new RegraVisibilidade(observador, titular, escopo));
	}
}
