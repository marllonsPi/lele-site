package br.com.crispimcampos.gestao.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleUnidadeTest {

	static String chave() {
		byte[] b = new byte[32];
		new SecureRandom().nextBytes(b);
		return Base64.getEncoder().encodeToString(b);
	}

	@Test
	void cifraIdaEVolta() {
		var c = new CifraToken(chave());
		byte[] x = c.cifrar("refresh-ficticio", "conexao_google:1");
		assertThat(new String(x, StandardCharsets.ISO_8859_1)).doesNotContain("refresh-ficticio");
		assertThat(c.decifrar(x, "conexao_google:1")).isEqualTo("refresh-ficticio");
		assertThat(c.cifrar("refresh-ficticio", "conexao_google:1")).as("IV aleatorio").isNotEqualTo(x);
	}

	@Test
	void cifraRecusaDonoErradoAdulteracaoEOutraChave() {
		var c = new CifraToken(chave());
		byte[] x = c.cifrar("t", "conexao_google:1");
		assertThatThrownBy(() -> c.decifrar(x, "conexao_google:2")).isInstanceOf(IllegalStateException.class);
		byte[] adulterado = x.clone();
		adulterado[adulterado.length - 1] ^= 1;
		assertThatThrownBy(() -> c.decifrar(adulterado, "conexao_google:1")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CifraToken(chave()).decifrar(x, "conexao_google:1")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cifraExigeChaveDe32Bytes() {
		assertThatThrownBy(() -> new CifraToken(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CifraToken("nao e base64 !!")).isInstanceOf(IllegalArgumentException.class);
	}

	static String b64(String s) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void mensagemExtraiAssuntoRemetenteDataETextoPlano() {
		var m = MensagemGmail.de(Map.of("id", "m1", "internalDate", "1760000000000", "payload", Map.of(
				"mimeType", "multipart/alternative",
				"headers", List.of(Map.of("name", "Subject", "value", "Intimacao ficticia"), Map.of("name", "From", "value", "push@tribunal.jus.br")),
				"parts", List.of(Map.of("mimeType", "text/html", "body", Map.of("data", b64("<p>html</p>"))),
						Map.of("mimeType", "text/plain", "body", Map.of("data", b64("texto ficticio")))))));
		assertThat(m.id()).isEqualTo("m1");
		assertThat(m.assunto()).isEqualTo("Intimacao ficticia");
		assertThat(m.remetente()).isEqualTo("push@tribunal.jus.br");
		assertThat(m.texto()).isEqualTo("texto ficticio");
		assertThat(m.data()).isNotNull();
	}

	@Test
	void mensagemSoHtmlViraTextoSemTags() {
		var m = MensagemGmail.de(Map.of("id", "m2", "payload", Map.of("mimeType", "text/html",
				"body", Map.of("data", b64("<style>x{}</style><p>Ola <b>mundo</b>&nbsp;ficticio</p>")))));
		assertThat(m.texto()).isEqualTo("Ola mundo ficticio");
	}
}
