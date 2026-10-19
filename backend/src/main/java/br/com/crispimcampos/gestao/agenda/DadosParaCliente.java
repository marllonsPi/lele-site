package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unica porta do relatorio ao cliente para a agenda (invariante 6). O tipo de retorno e a lista branca: so tipo e datas
 * (e hora/modalidade/local da audiencia). Nao existem aqui observacoes, prazo interno, conferencias, nomes de quem
 * executou nem descricoes internas. Sem filtro de visibilidade: quem chama ja validou o acesso ao processo.
 */
@Service
public class DadosParaCliente {

	/** Algo que o escritorio fez no periodo: peca protocolada ou audiencia realizada. */
	public record Feito(TipoAgendamento tipo, LocalDate data) {
	}

	/** Audiencia marcada ou providencia prevista (com data limite legal). */
	public record ProximoPasso(TipoAgendamento tipo, LocalDate data, LocalTime hora, Modalidade modalidade, String localOuLink) {
	}

	private static final Set<TipoAgendamento> PECAS = Set.of(TipoAgendamento.INI, TipoAgendamento.CON, TipoAgendamento.MAN, TipoAgendamento.REC);
	private static final Set<StatusAgendamento> ABERTOS = Set.of(StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO,
			StatusAgendamento.PENDENTE, StatusAgendamento.DEVOLVIDO);
	private static final Set<StatusAgendamento> CONCLUIDOS = Set.of(StatusAgendamento.EM_CONFERENCIA_SUPERVISOR,
			StatusAgendamento.EM_CONFERENCIA_SOCIA, StatusAgendamento.CONFERIDO, StatusAgendamento.FINALIZADO);

	private final AgendamentoRepository agendamentos;
	private final java.time.Clock relogio;

	DadosParaCliente(AgendamentoRepository agendamentos, java.time.Clock relogio) {
		this.agendamentos = agendamentos;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public List<Feito> feitos(Long processoId, LocalDate de, LocalDate ate) {
		ZoneId zona = relogio.getZone();
		Instant ini = de == null ? Instant.EPOCH : de.atStartOfDay(zona).toInstant();
		Instant fim = ate.plusDays(1).atStartOfDay(zona).toInstant();
		return agendamentos.findByProcessoIdAndFinalizadoEmGreaterThanEqualAndFinalizadoEmLessThan(processoId, ini, fim).stream()
				.filter(a -> CONCLUIDOS.contains(a.getStatus()))
				.filter(a -> PECAS.contains(a.getTipo()) || a.getTipo() == TipoAgendamento.AUD)
				.map(a -> new Feito(a.getTipo(), a.getFinalizadoEm().atZone(zona).toLocalDate()))
				.sorted(java.util.Comparator.comparing(Feito::data)).toList();
	}

	@Transactional(readOnly = true)
	public List<ProximoPasso> proximosPassos(Long processoId, LocalDate hoje) {
		ZoneId zona = relogio.getZone();
		return agendamentos.findByProcessoIdAndStatusIn(processoId, ABERTOS).stream().map(a -> {
			if (a.getTipo() == TipoAgendamento.AUD) {
				var local = a.getInicioEm().atZone(zona);
				return local.toLocalDate().isBefore(hoje) ? null
						: new ProximoPasso(TipoAgendamento.AUD, local.toLocalDate(), local.toLocalTime(), a.getModalidade(), a.getLocalOuLink());
			}
			if (PECAS.contains(a.getTipo()) && a.getPrazoFatal() != null && !a.getPrazoFatal().isBefore(hoje)) {
				return new ProximoPasso(a.getTipo(), a.getPrazoFatal(), null, null, null);
			}
			return null;
		}).filter(java.util.Objects::nonNull).sorted(java.util.Comparator.comparing(ProximoPasso::data)).toList();
	}
}
