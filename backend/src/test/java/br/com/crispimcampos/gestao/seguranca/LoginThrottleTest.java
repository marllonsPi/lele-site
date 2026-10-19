package br.com.crispimcampos.gestao.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

	/** Relogio que anda quando mandamos. */
	static class Relogio extends Clock {
		Instant agora = Instant.parse("2026-10-20T12:00:00Z");

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return agora;
		}
	}

	@Test
	void bloqueiaNaQuintaFalhaELiberaDepoisDoBloqueio() {
		Relogio r = new Relogio();
		var t = new LoginThrottle(r);
		for (int i = 0; i < LoginThrottle.MAXIMO - 1; i++) {
			t.falhou("1.2.3.4", "a@exemplo.test");
			assertThat(t.bloqueado("1.2.3.4", "a@exemplo.test")).isFalse();
		}
		t.falhou("1.2.3.4", "A@Exemplo.test");
		assertThat(t.bloqueado("1.2.3.4", "a@exemplo.test")).as("e-mail sem diferenciar maiusculas").isTrue();
		assertThat(t.bloqueado("9.9.9.9", "a@exemplo.test")).as("outro IP nao e afetado").isFalse();
		assertThat(t.bloqueado("1.2.3.4", "b@exemplo.test")).as("outro e-mail nao e afetado").isFalse();
		r.agora = r.agora.plus(LoginThrottle.BLOQUEIO).plusSeconds(1);
		assertThat(t.bloqueado("1.2.3.4", "a@exemplo.test")).isFalse();
	}

	@Test
	void falhasAntigasSaemDaJanelaESucessoLimpa() {
		Relogio r = new Relogio();
		var t = new LoginThrottle(r);
		for (int i = 0; i < LoginThrottle.MAXIMO - 1; i++) {
			t.falhou("ip", "a@exemplo.test");
		}
		r.agora = r.agora.plus(LoginThrottle.JANELA).plus(Duration.ofSeconds(5));
		t.falhou("ip", "a@exemplo.test");
		assertThat(t.bloqueado("ip", "a@exemplo.test")).as("as 4 antigas expiraram").isFalse();
		for (int i = 0; i < 3; i++) {
			t.falhou("ip", "a@exemplo.test");
		}
		t.sucesso("ip", "a@exemplo.test");
		t.falhou("ip", "a@exemplo.test");
		assertThat(t.bloqueado("ip", "a@exemplo.test")).isFalse();
	}
}
