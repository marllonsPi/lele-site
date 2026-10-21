package br.com.crispimcampos.gestao.google;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EscopoDriveTest {

	@Test
	void escopoDriveNaoSeConfundeComSomenteLeitura() {
		String leitura = "openid email https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/drive.readonly";
		String completo = "openid email https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/drive";
		assertThat(GoogleContasService.temEscopo(leitura, GoogleContasService.ESCOPO_DRIVE)).isFalse();
		assertThat(GoogleContasService.temEscopo(completo, GoogleContasService.ESCOPO_DRIVE)).isTrue();
		assertThat(GoogleContasService.temEscopo(null, GoogleContasService.ESCOPO_DRIVE)).isFalse();
	}

	@Test
	void jsonDeNomeDeArquivoEscapaAspasEBarras() {
		assertThat(GoogleApi.jsonTexto("Acordo \"final\" \\ nova\nlinha")).isEqualTo("\"Acordo \\\"final\\\" \\\\ nova\\nlinha\"");
	}
}
