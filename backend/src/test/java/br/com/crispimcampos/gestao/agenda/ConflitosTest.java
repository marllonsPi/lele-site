package br.com.crispimcampos.gestao.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConflitosTest {

	static Instant h(String hhmm) {
		return Instant.parse("2026-10-20T" + hhmm + ":00Z");
	}

	static Conflitos.Intervalo i(Long id, TipoAgendamento t, long adv, String ini, String fim) {
		return new Conflitos.Intervalo(id, t, adv, h(ini), h(fim));
	}

	@Test
	void sobreposicaoParcialETotalContam_encostarNao() {
		var a = i(null, TipoAgendamento.ATD, 1, "10:00", "11:00");
		assertThat(Conflitos.sobrepoe(a, i(2L, TipoAgendamento.ATD, 1, "10:30", "11:30"))).isTrue();
		assertThat(Conflitos.sobrepoe(a, i(2L, TipoAgendamento.ATD, 1, "10:15", "10:45"))).isTrue();
		assertThat(Conflitos.sobrepoe(a, i(2L, TipoAgendamento.ATD, 1, "11:00", "12:00"))).as("encostar nao e conflito").isFalse();
		assertThat(Conflitos.sobrepoe(a, i(2L, TipoAgendamento.ATD, 1, "09:00", "10:00"))).isFalse();
	}

	@Test
	void mesmoAdvogadoConflitaEntreAudienciaEAtendimento() {
		var novo = i(null, TipoAgendamento.ATD, 1, "10:00", "11:00");
		var avisos = Conflitos.detectar(novo, List.of(i(5L, TipoAgendamento.AUD, 1, "10:30", "12:00")));
		assertThat(avisos).hasSize(1);
		assertThat(avisos.get(0).tipo()).isEqualTo(Conflitos.Tipo.MESMO_ADVOGADO);
		assertThat(avisos.get(0).comTipo()).isEqualTo(TipoAgendamento.AUD);
	}

	@Test
	void audienciasSobrepostasDeAdvogadosDiferentesConflitamNoEscritorio() {
		var novo = i(null, TipoAgendamento.AUD, 1, "14:00", "15:00");
		var avisos = Conflitos.detectar(novo, List.of(i(5L, TipoAgendamento.AUD, 2, "14:30", "15:30")));
		assertThat(avisos).extracting(Conflitos.Aviso::tipo).containsExactly(Conflitos.Tipo.AUDIENCIAS_NO_ESCRITORIO);
	}

	@Test
	void atendimentosDeAdvogadosDiferentesNaoConflitam() {
		var novo = i(null, TipoAgendamento.ATD, 1, "10:00", "11:00");
		assertThat(Conflitos.detectar(novo, List.of(i(5L, TipoAgendamento.ATD, 2, "10:00", "11:00")))).isEmpty();
		assertThat(Conflitos.detectar(novo, List.of(i(5L, TipoAgendamento.AUD, 2, "10:00", "11:00"))))
				.as("audiencia de outro advogado nao conflita com atendimento").isEmpty();
	}

	@Test
	void ignoraOProprioItemAoReavaliar() {
		var existente = i(7L, TipoAgendamento.AUD, 1, "10:00", "11:00");
		assertThat(Conflitos.detectar(existente, List.of(existente))).isEmpty();
	}
}
