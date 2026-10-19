package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agenda individual (item 7): afazeres em aberto de uma pessoa, com cor de status, urgencia e alerta de
 * audiencia (10 dias). Prazo aparece na data de trabalho (prazo interno). Itens vencidos ha mais de 30 dias
 * saem da agenda (pendencia 2) mas continuam nos dados. Socia pode ver a agenda de outra pessoa.
 */
@Service
public class AgendaService {

	private static final Set<StatusAgendamento> ABERTOS = Set.of(StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO,
			StatusAgendamento.PENDENTE, StatusAgendamento.DEVOLVIDO);

	private final AgendamentoRepository agendamentos;
	private final Visibilidade visibilidade;
	private final Clock relogio;

	AgendaService(AgendamentoRepository agendamentos, Visibilidade visibilidade, Clock relogio) {
		this.agendamentos = agendamentos;
		this.visibilidade = visibilidade;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public List<AgendaItemDto> agenda(UsuarioLogado ator, Long usuarioId) {
		Long alvo = usuarioId == null ? ator.id() : usuarioId;
		if (!alvo.equals(ator.id()) && ator.perfil() != Perfil.SOCIA) {
			throw RegrasAgendamento.proibido("Somente socias veem a agenda de outra pessoa");
		}
		List<Agendamento> itens;
		if (ator.perfil() == Perfil.ESTAGIARIO) {
			itens = agendamentos.findAgendaAtribuida(ator.id(), ABERTOS);
		} else {
			Set<Long> titulares = visibilidade.titularesVisiveis(ator, Escopo.AGENDAMENTO);
			if (titulares.isEmpty()) {
				return List.of();
			}
			itens = agendamentos.findAgenda(alvo, titulares, ABERTOS);
		}
		LocalDate hoje = LocalDate.now(relogio);
		return itens.stream().map(a -> AgendaItemDto.de(a, hoje, relogio.getZone()))
				.filter(i -> !i.data().isBefore(hoje.minusDays(StatusVisual.DIAS_VENCIDO_VISIVEL)))
				.sorted(ORDEM).toList();
	}

	/** Por dia; no dia, compromissos com hora primeiro e em ordem de horario; depois urgentes. */
	static final Comparator<AgendaItemDto> ORDEM = Comparator.comparing(AgendaItemDto::data)
			.thenComparing(AgendaItemDto::hora, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(AgendaItemDto::urgente, Comparator.reverseOrder())
			.thenComparing(AgendaItemDto::id);
}
