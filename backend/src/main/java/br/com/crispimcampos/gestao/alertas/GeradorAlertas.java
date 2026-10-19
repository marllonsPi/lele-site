package br.com.crispimcampos.gestao.alertas;

import br.com.crispimcampos.gestao.agenda.AgendaItemDto;
import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Regras do item 9, dominio puro: vespera do prazo interno e do fatal; audiencia 10 dias antes e na vespera; atendimento na
 * vespera; resumo diario de vencidos. O responsavel recebe e as socias vao em copia. As mensagens nao trazem nome de
 * cliente nem conteudo da publicacao: so numero do processo, tipo e data.
 */
final class GeradorAlertas {

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private GeradorAlertas() {
	}

	/** @param processo numero formatado do processo (ou null) a partir do id */
	static List<NovaNotificacao> gerar(List<AgendaItemDto> abertos, LocalDate hoje, Set<Long> socias, Function<Long, String> processo) {
		List<NovaNotificacao> saida = new ArrayList<>();
		for (AgendaItemDto i : abertos) {
			if (i.tipo().ehPrazo()) {
				if (i.prazoInterno() != null && i.prazoInterno().minusDays(1).equals(hoje)) {
					adicionar(saida, TipoAlerta.VESPERA_PRAZO_INTERNO, i, socias, "Prazo interno amanha (" + i.tipo() + ")",
							linha(i, processo, "O prazo interno vence amanha, " + i.prazoInterno().format(DATA) + "."));
				}
				if (i.prazoFatal() != null && i.prazoFatal().minusDays(1).equals(hoje)) {
					adicionar(saida, TipoAlerta.VESPERA_PRAZO_FATAL, i, socias, "PRAZO FATAL amanha (" + i.tipo() + ")",
							linha(i, processo, "O prazo fatal vence amanha, " + i.prazoFatal().format(DATA) + "."));
				}
			} else if (i.tipo() == TipoAgendamento.AUD) {
				if (i.data().minusDays(10).equals(hoje)) {
					adicionar(saida, TipoAlerta.AUDIENCIA_10_DIAS, i, socias, "Audiencia em 10 dias",
							linha(i, processo, "Audiencia em " + i.data().format(DATA) + " as " + i.hora() + " (" + i.modalidade() + ")."));
				}
				if (i.data().minusDays(1).equals(hoje)) {
					adicionar(saida, TipoAlerta.AUDIENCIA_VESPERA, i, socias, "Audiencia amanha",
							linha(i, processo, "Audiencia amanha, " + i.data().format(DATA) + " as " + i.hora() + " (" + i.modalidade() + ")."));
				}
			} else if (i.tipo() == TipoAgendamento.ATD && i.data().minusDays(1).equals(hoje)) {
				adicionar(saida, TipoAlerta.ATENDIMENTO_VESPERA, i, socias, "Atendimento amanha",
						"Atendimento de cliente amanha, " + i.data().format(DATA) + " as " + i.hora() + " (" + i.modalidade() + ").");
			}
		}
		resumoDeVencidos(abertos, hoje, socias, saida);
		return saida;
	}

	private static void resumoDeVencidos(List<AgendaItemDto> abertos, LocalDate hoje, Set<Long> socias, List<NovaNotificacao> saida) {
		Map<Long, Long> vencidosPorResponsavel = new LinkedHashMap<>();
		for (AgendaItemDto i : abertos) {
			if (i.data().isBefore(hoje)) {
				vencidosPorResponsavel.merge(i.responsavelId(), 1L, Long::sum);
			}
		}
		long total = 0;
		for (var e : vencidosPorResponsavel.entrySet()) {
			total += e.getValue();
			saida.add(new NovaNotificacao(TipoAlerta.RESUMO_VENCIDOS, e.getKey(), e.getKey(), "Resumo: itens vencidos",
					"Voce tem " + e.getValue() + " item(ns) vencido(s) e nao finalizado(s)."));
		}
		if (total > 0) {
			for (Long socia : socias) {
				saida.add(new NovaNotificacao(TipoAlerta.RESUMO_VENCIDOS, 0, socia, "Resumo: itens vencidos do escritorio",
						"O escritorio tem " + total + " item(ns) vencido(s) e nao finalizado(s), de " + vencidosPorResponsavel.size()
								+ " pessoa(s)."));
			}
		}
	}

	private static void adicionar(List<NovaNotificacao> saida, TipoAlerta tipo, AgendaItemDto i, Set<Long> socias, String assunto, String corpo) {
		Set<Long> destinatarios = new LinkedHashSet<>();
		destinatarios.add(i.responsavelId());
		destinatarios.addAll(socias);
		for (Long d : destinatarios) {
			saida.add(new NovaNotificacao(tipo, i.id(), d, assunto, corpo));
		}
	}

	private static String linha(AgendaItemDto i, Function<Long, String> processo, String texto) {
		String numero = i.processoId() == null ? null : processo.apply(i.processoId());
		return (numero == null ? "" : "Processo " + numero + ". ") + texto + (i.urgente() ? " URGENTE." : "");
	}
}
