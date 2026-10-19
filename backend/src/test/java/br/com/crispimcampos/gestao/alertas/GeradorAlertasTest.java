package br.com.crispimcampos.gestao.alertas;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.crispimcampos.gestao.agenda.AgendaItemDto;
import br.com.crispimcampos.gestao.agenda.Modalidade;
import br.com.crispimcampos.gestao.agenda.StatusAgendamento;
import br.com.crispimcampos.gestao.agenda.StatusVisual;
import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeradorAlertasTest {

	static final LocalDate HOJE = LocalDate.parse("2026-10-20");
	static final Set<Long> SOCIAS = Set.of(1L, 2L);

	static AgendaItemDto prazo(long id, long resp, LocalDate interno, LocalDate fatal) {
		return new AgendaItemDto(id, 10L, null, TipoAgendamento.CON, "x", null, resp, 1L, false, StatusAgendamento.PENDENTE,
				interno, null, null, null, null, interno.minusDays(5), interno, fatal, StatusVisual.NENHUM, false, false);
	}

	static AgendaItemDto compromisso(long id, TipoAgendamento tipo, long resp, LocalDate dia) {
		return new AgendaItemDto(id, tipo == TipoAgendamento.AUD ? 10L : null, null, tipo, "x", null, resp, 1L, false,
				StatusAgendamento.PENDENTE, dia, LocalTime.of(10, 0), 60, Modalidade.ONLINE, "x", null, null, null,
				StatusVisual.NENHUM, false, false);
	}

	List<NovaNotificacao> gerar(AgendaItemDto... itens) {
		return GeradorAlertas.gerar(List.of(itens), HOJE, SOCIAS, id -> "0001234-56.2025.8.07.0001");
	}

	@Test
	void vesperaDoPrazoInternoAvisaOResponsavelComCopiaAsSocias() {
		var r = gerar(prazo(1, 3, HOJE.plusDays(1), HOJE.plusDays(4)));
		assertThat(r).extracting(NovaNotificacao::tipo).containsOnly(TipoAlerta.VESPERA_PRAZO_INTERNO);
		assertThat(r).extracting(NovaNotificacao::destinatarioId).containsExactlyInAnyOrder(3L, 1L, 2L);
		assertThat(r.get(0).corpo()).contains("0001234-56.2025.8.07.0001").contains("21/10/2026");
	}

	@Test
	void responsavelQueESociaNaoRecebeEmDobro() {
		var r = gerar(prazo(1, 1, HOJE.plusDays(1), HOJE.plusDays(4)));
		assertThat(r).extracting(NovaNotificacao::destinatarioId).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	void vesperaDoFatalEDoInternoNoMesmoDiaGeramDoisAlertas() {
		var r = gerar(prazo(1, 3, HOJE.plusDays(1), HOJE.plusDays(1)));
		assertThat(r).extracting(NovaNotificacao::tipo).contains(TipoAlerta.VESPERA_PRAZO_INTERNO, TipoAlerta.VESPERA_PRAZO_FATAL);
	}

	@Test
	void naoAvisaForaDaVespera() {
		assertThat(gerar(prazo(1, 3, HOJE.plusDays(2), HOJE.plusDays(5)))).isEmpty();
		assertThat(gerar(prazo(1, 3, HOJE, HOJE.plusDays(3)))).as("no proprio dia nao e vespera").isEmpty();
	}

	@Test
	void audienciaAvisaA10DiasEnaVespera() {
		assertThat(gerar(compromisso(1, TipoAgendamento.AUD, 3, HOJE.plusDays(10)))).extracting(NovaNotificacao::tipo)
				.containsOnly(TipoAlerta.AUDIENCIA_10_DIAS);
		assertThat(gerar(compromisso(1, TipoAgendamento.AUD, 3, HOJE.plusDays(1)))).extracting(NovaNotificacao::tipo)
				.containsOnly(TipoAlerta.AUDIENCIA_VESPERA);
		assertThat(gerar(compromisso(1, TipoAgendamento.AUD, 3, HOJE.plusDays(9)))).isEmpty();
	}

	@Test
	void atendimentoAvisaNaVesperaSemDadosDoCliente() {
		var r = gerar(compromisso(1, TipoAgendamento.ATD, 3, HOJE.plusDays(1)));
		assertThat(r).extracting(NovaNotificacao::tipo).containsOnly(TipoAlerta.ATENDIMENTO_VESPERA);
		assertThat(r.get(0).corpo()).doesNotContain("Processo");
	}

	@Test
	void resumoDeVencidosPorResponsavelEConsolidadoParaSocias() {
		var r = gerar(prazo(1, 3, HOJE.minusDays(2), HOJE.plusDays(2)), prazo(2, 3, HOJE.minusDays(1), HOJE.plusDays(2)),
				prazo(3, 4, HOJE.minusDays(9), HOJE.plusDays(2)), prazo(4, 4, HOJE.plusDays(8), HOJE.plusDays(12)));
		var resumos = r.stream().filter(n -> n.tipo() == TipoAlerta.RESUMO_VENCIDOS).toList();
		assertThat(resumos).extracting(NovaNotificacao::destinatarioId).containsExactlyInAnyOrder(3L, 4L, 1L, 2L);
		var doResponsavel3 = resumos.stream().filter(n -> n.destinatarioId() == 3L).findFirst().orElseThrow();
		assertThat(doResponsavel3.corpo()).contains("2 item");
		assertThat(resumos.stream().filter(n -> n.escopoId() == 0)).hasSize(2);
		assertThat(resumos.stream().filter(n -> n.escopoId() == 0).findFirst().orElseThrow().corpo()).contains("3 item");
	}

	@Test
	void semVencidosNaoHaResumo() {
		assertThat(gerar(prazo(1, 3, HOJE.plusDays(8), HOJE.plusDays(12)))).isEmpty();
	}
}
