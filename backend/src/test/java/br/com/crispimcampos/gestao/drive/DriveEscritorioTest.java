package br.com.crispimcampos.gestao.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DriveEscritorioTest {

	@Test
	void extraiOIdDaPastaDeUmLinkOuDeUmIdPuro() {
		assertThat(DriveEscritorio.extrairId("https://drive.google.com/drive/folders/1WgkeEbFfyhrcAexByPXudRDyuv1zg5ZP?usp=sharing")).isEqualTo("1WgkeEbFfyhrcAexByPXudRDyuv1zg5ZP");
		assertThat(DriveEscritorio.extrairId("1WgkeEbFfyhrcAexByPXudRDyuv1zg5ZP")).isEqualTo("1WgkeEbFfyhrcAexByPXudRDyuv1zg5ZP");
		assertThatThrownBy(() -> DriveEscritorio.extrairId("nao e um link")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void nomesDePastaSaoLimposEComparadosSemAcento() {
		assertThat(DriveEscritorio.nomePasta("  Maria/José  \n da Silva ")).isEqualTo("Maria José da Silva");
		assertThat(DriveEscritorio.chave("José  Álvaro")).isEqualTo(DriveEscritorio.chave("JOSE ALVARO"));
		assertThat(DriveEscritorio.caminho("A", null, " ", "B")).containsExactly("A", "B");
	}
}
