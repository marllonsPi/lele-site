package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import org.springframework.stereotype.Component;

/** Acesso e atribuicao comuns a prazos, compromissos, observacoes e anexos. */
@Component
class RegrasAgendamento {

	private final AgendamentoRepository agendamentos;
	private final Visibilidade visibilidade;
	private final UsuarioService usuarios;

	RegrasAgendamento(AgendamentoRepository agendamentos, Visibilidade visibilidade, UsuarioService usuarios) {
		this.agendamentos = agendamentos;
		this.visibilidade = visibilidade;
		this.usuarios = usuarios;
	}

	/** Item visivel ao usuario (escopo AGENDAMENTO; estagiario so o atribuido). Fora disso responde como inexistente. */
	Agendamento obterVisivel(UsuarioLogado usuario, Long id) {
		Agendamento a = agendamentos.findById(id).orElseThrow(() -> new AgendaNaoEncontradaException("Agendamento nao encontrado"));
		if (!visivel(usuario, a)) {
			throw new AgendaNaoEncontradaException("Agendamento nao encontrado");
		}
		return a;
	}

	boolean visivel(UsuarioLogado usuario, Agendamento a) {
		return usuario.perfil() == Perfil.ESTAGIARIO ? usuario.id().equals(a.getResponsavelId())
				: visibilidade.podeVer(usuario, Escopo.AGENDAMENTO, a.getTitularId());
	}

	/**
	 * Invariante 7 e distribuicao: socia atribui a qualquer usuario ativo; assistente a si mesma ou a estagiario
	 * (nunca a advogado ou socia); advogado a si mesmo; estagiario a ninguem.
	 */
	UsuarioDto validarResponsavel(UsuarioLogado ator, Long responsavelId) {
		if (responsavelId == null) {
			throw regra("Informe o responsavel");
		}
		UsuarioDto r;
		try {
			r = usuarios.buscar(responsavelId);
		} catch (RuntimeException e) {
			throw regra("Responsavel nao encontrado");
		}
		if (!r.ativo()) {
			throw regra("Responsavel desativado");
		}
		boolean permitido = switch (ator.perfil()) {
			case SOCIA -> true;
			case ASSISTENTE -> responsavelId.equals(ator.id()) || r.perfil() == Perfil.ESTAGIARIO;
			case ADVOGADO -> responsavelId.equals(ator.id());
			case ESTAGIARIO -> false;
		};
		if (!permitido) {
			throw proibido("Voce nao pode atribuir tarefa a este usuario");
		}
		return r;
	}

	static RegraDeAgendaException regra(String m) {
		return new RegraDeAgendaException(m, false);
	}

	static RegraDeAgendaException proibido(String m) {
		return new RegraDeAgendaException(m, true);
	}
}
