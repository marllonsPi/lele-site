package br.com.crispimcampos.gestao.contas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {

	/** Vetores do RFC 6238 (SHA-1, segredo ASCII "12345678901234567890"), 6 ultimos digitos. */
	@Test
	void vetoresOficiaisDoRfc6238() {
		byte[] segredo = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
		assertThat(Totp.codigo(segredo, 59L)).isEqualTo("287082");
		assertThat(Totp.codigo(segredo, 1111111109L)).isEqualTo("081804");
		assertThat(Totp.codigo(segredo, 1111111111L)).isEqualTo("050471");
		assertThat(Totp.codigo(segredo, 1234567890L)).isEqualTo("005924");
		assertThat(Totp.codigo(segredo, 2000000000L)).isEqualTo("279037");
		assertThat(Totp.codigo(segredo, 20000000000L)).isEqualTo("353130");
	}

	@Test
	void base32IdaEVolta() {
		byte[] original = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
		String b32 = Totp.base32(original);
		assertThat(b32).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
		assertThat(Totp.decodificar(b32)).isEqualTo(original);
		assertThat(Totp.decodificar(Totp.novoSegredo())).hasSize(20);
	}

	@Test
	void validaComJanelaDeUmPassoERecusaOResto() {
		String segredo = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
		Instant agora = Instant.ofEpochSecond(1111111109L);
		assertThat(Totp.valido(segredo, "081804", agora)).isTrue();
		assertThat(Totp.valido(segredo, "081804", agora.plusSeconds(25))).as("passo seguinte ainda aceito").isTrue();
		assertThat(Totp.valido(segredo, "081804", agora.plusSeconds(120))).as("fora da janela").isFalse();
		assertThat(Totp.valido(segredo, "000000", agora)).isFalse();
		assertThat(Totp.valido(segredo, "12345", agora)).isFalse();
		assertThat(Totp.valido(segredo, "abcdef", agora)).isFalse();
		assertThat(Totp.valido(segredo, null, agora)).isFalse();
	}

	@Test
	void uriParaOAplicativoAutenticador() {
		String uri = Totp.uri("Crispim & Campos", "socia@exemplo.test", "ABC");
		assertThat(uri).startsWith("otpauth://totp/").contains("secret=ABC").contains("issuer=Crispim%20%26%20Campos").contains("digits=6");
	}
}
