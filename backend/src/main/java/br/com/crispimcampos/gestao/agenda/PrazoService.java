package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.cadastros.ProcessoResumo;
import br.com.crispimcampos.gestao.calendario.CalendarioException;
import br.com.crispimcampos.gestao.calendario.CalendarioService;
import br.com.crispimcampos.gestao.calendario.ContextoCalendario;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prazos (item 6). Prazo interno = fatal menos 3 dias uteis no calendario do tribunal do processo (invariante 5);
 * dupla conferencia: quem lancou nao confirma e estagiario nunca confirma (invariante 2). O prazo fatal e
 * informado por quem lanca (pendencia 1: se passa a ser calculado, muda aqui). Leitura por
 * {@link Visibilidade} no escopo AGENDAMENTO (invariante 1); estagiario ve so o que lhe foi atribuido.
 */
@Service
public class PrazoService {

	static final int DIAS_UTEIS_INTERNO = 3;

	private final AgendamentoRepository agendamentos;
	private final CadastroService cadastros;
	private final CalendarioService calendario;
	private final Visibilidade visibilidade;
	private final UsuarioService usuarios;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	PrazoService(AgendamentoRepository agendamentos, CadastroService cadastros, CalendarioService calendario,
			Visibilidade visibilidade, UsuarioService usuarios, AuditoriaService auditoria, Clock relogio) {
		this.agendamentos = agendamentos;
		this.cadastros = cadastros;
		this.calendario = calendario;
		this.visibilidade = visibilidade;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public List<PrazoDto> listar(UsuarioLogado usuario) {
		if (usuario.perfil() == Perfil.ESTAGIARIO) {
			return agendamentos.findByResponsavelIdOrderByPrazoInternoAscIdAsc(usuario.id()).stream().map(PrazoDto::de).toList();
		}
		Set<Long> titulares = visibilidade.titularesVisiveis(usuario, Escopo.AGENDAMENTO);
		if (titulares.isEmpty()) {
			return List.of();
		}
		return agendamentos.findByTitularIdInOrderByPrazoInternoAscIdAsc(titulares).stream().map(PrazoDto::de).toList();
	}

	@Transactional(readOnly = true)
	public PrazoDto buscar(UsuarioLogado usuario, Long id) {
		return PrazoDto.de(obterVisivel(usuario, id));
	}

	@Transactional
	public PrazoDto lancar(UsuarioLogado ator, Long processoId, TipoAgendamento tipo, String descricao,
			Long responsavelId, boolean urgente, LocalDate inicio, LocalDate fatal) {
		if (!tipo.ehPrazo()) {
			throw regra("Audiencias e atendimentos tem data e hora proprias; use o agendamento da Fase 4");
		}
		if (ator.perfil() == Perfil.ESTAGIARIO) {
			throw proibido("Estagiario lanca prazo apenas em tarefas que lhe forem distribuidas");
		}
		ProcessoResumo processo = cadastros.resumo(processoId);
		if (!processo.ativo()) {
			throw regra("Processo desativado");
		}
		if (!visibilidade.podeVer(ator, Escopo.AGENDAMENTO, processo.titularId())) {
			throw new AgendaNaoEncontradaException("Processo nao encontrado");
		}
		validarResponsavel(ator, responsavelId);
		LocalDate interno = calcularInterno(processo, inicio, fatal);
		Agendamento a = agendamentos.save(Agendamento.lancarPrazo(processoId, processo.titularId(), tipo,
				limpo(descricao), responsavelId, ator.id(), urgente, inicio, interno, fatal));
		auditoria.registrar(ator.id(), "PRAZO_LANCADO", "agendamento", String.valueOf(a.getId()), "tipo=" + tipo);
		return PrazoDto.de(a);
	}

	/** Corrige datas/responsavel enquanto aguarda conferencia; so quem lancou (ou socia) corrige. */
	@Transactional
	public PrazoDto corrigir(UsuarioLogado ator, Long id, String descricao, Long responsavelId, boolean urgente,
			LocalDate inicio, LocalDate fatal) {
		Agendamento a = obterVisivel(ator, id);
		if (!ator.id().equals(a.getLancadoPor()) && ator.perfil() != Perfil.SOCIA) {
			throw proibido("Somente quem lancou o prazo (ou uma socia) pode corrigi-lo");
		}
		if (a.getStatus() != StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO) {
			throw regra("Prazo ja conferido nao pode ser alterado");
		}
		validarResponsavel(ator, responsavelId);
		ProcessoResumo processo = cadastros.resumo(a.getProcessoId());
		a.corrigirPrazo(limpo(descricao), responsavelId, urgente, inicio, calcularInterno(processo, inicio, fatal), fatal);
		auditoria.registrar(ator.id(), "PRAZO_CORRIGIDO", "agendamento", String.valueOf(id), null);
		return PrazoDto.de(a);
	}

	@Transactional
	public PrazoDto conferir(UsuarioLogado ator, Long id) {
		if (ator.perfil() == Perfil.ESTAGIARIO) {
			throw proibido("Estagiario nao confirma prazos");
		}
		Agendamento a = obterVisivel(ator, id);
		if (ator.id().equals(a.getLancadoPor())) {
			throw proibido("Quem lancou o prazo nao pode confirma-lo");
		}
		if (a.getStatus() != StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO) {
			throw regra("O prazo nao aguarda conferencia");
		}
		a.conferirPrazo(ator.id(), relogio.instant());
		auditoria.registrar(ator.id(), "PRAZO_CONFERIDO", "agendamento", String.valueOf(id), null);
		return PrazoDto.de(a);
	}

	// ---- internos

	private LocalDate calcularInterno(ProcessoResumo processo, LocalDate inicio, LocalDate fatal) {
		if (inicio == null || fatal == null) {
			throw regra("Informe o inicio do prazo e o prazo fatal");
		}
		if (fatal.isBefore(inicio)) {
			throw regra("O prazo fatal nao pode ser anterior ao inicio do prazo");
		}
		try {
			return calendario.subtrairDiasUteis(ContextoCalendario.deTribunal(processo.tribunal()), fatal,
					DIAS_UTEIS_INTERNO);
		} catch (CalendarioException e) {
			throw regra(e.getMessage());
		}
	}

	/**
	 * Invariante 7 e distribuicao: socia atribui a qualquer usuario ativo; assistente a si mesma ou a estagiario
	 * (nunca a advogado ou socia); advogado a si mesmo.
	 */
	private void validarResponsavel(UsuarioLogado ator, Long responsavelId) {
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
	}

	private Agendamento obterVisivel(UsuarioLogado usuario, Long id) {
		Agendamento a = agendamentos.findById(id).orElseThrow(() -> new AgendaNaoEncontradaException("Prazo nao encontrado"));
		boolean visivel = usuario.perfil() == Perfil.ESTAGIARIO ? usuario.id().equals(a.getResponsavelId())
				: visibilidade.podeVer(usuario, Escopo.AGENDAMENTO, a.getTitularId());
		if (!visivel) {
			throw new AgendaNaoEncontradaException("Prazo nao encontrado");
		}
		return a;
	}

	private static String limpo(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static RegraDeAgendaException regra(String m) {
		return new RegraDeAgendaException(m, false);
	}

	private static RegraDeAgendaException proibido(String m) {
		return new RegraDeAgendaException(m, true);
	}
}
