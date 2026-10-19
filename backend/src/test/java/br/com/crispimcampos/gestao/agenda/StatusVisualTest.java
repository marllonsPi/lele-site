package br.com.crispimcampos.gestao.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StatusVisualTest {

	static final LocalDate HOJE = LocalDate.parse("2026-10-20");

	@Test
	void coresDaSpec() {
		assertThat(StatusVisual.de(HOJE, HOJE)).isEqualTo(StatusVisual.AMARELO);
		assertThat(StatusVisual.de(HOJE.plusDays(1), HOJE)).isEqualTo(StatusVisual.VERDE);
		assertThat(StatusVisual.de(HOJE.minusDays(1), HOJE)).isEqualTo(StatusVisual.VERMELHO);
		assertThat(StatusVisual.de(HOJE.minusDays(30), HOJE)).as("limite de 30 dias ainda vermelho").isEqualTo(StatusVisual.VERMELHO);
		assertThat(StatusVisual.de(HOJE.minusDays(31), HOJE)).isEqualTo(StatusVisual.NENHUM);
		assertThat(StatusVisual.de(HOJE.plusDays(2), HOJE)).isEqualTo(StatusVisual.NENHUM);
	}

	@Test
	void coresDosTiposDaSpec() {
		assertThat(TipoAgendamento.CTR.cor()).isEqualTo("preto");
		assertThat(TipoAgendamento.INI.cor()).isEqualTo("rosa");
		assertThat(TipoAgendamento.CON.cor()).isEqualTo("laranja");
		assertThat(TipoAgendamento.MAN.cor()).isEqualTo("cinza");
		assertThat(TipoAgendamento.AUD.cor()).isEqualTo("roxo");
		assertThat(TipoAgendamento.REC.cor()).isEqualTo("marrom");
		assertThat(TipoAgendamento.ATD.cor()).isEqualTo("azul");
	}
}
