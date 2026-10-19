package br.com.crispimcampos.gestao.cadastros;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValidadoresTest {

	@Test
	void cnjValidoEmQualquerFormatacao() {
		assertThat(NumeroCnj.valido("0002080-25.2012.5.15.0049")).isTrue();
		assertThat(NumeroCnj.valido("00020802520125150049")).isTrue();
		assertThat(NumeroCnj.formatar("00020802520125150049")).isEqualTo("0002080-25.2012.5.15.0049");
	}

	@Test
	void cnjInvalido() {
		assertThat(NumeroCnj.valido("0002080-26.2012.5.15.0049")).as("digito errado").isFalse();
		assertThat(NumeroCnj.valido("123")).isFalse();
		assertThat(NumeroCnj.valido(null)).isFalse();
	}

	@Test
	void cpfECnpj() {
		assertThat(Documento.valido("529.982.247-25")).isTrue();
		assertThat(Documento.valido("11.222.333/0001-81")).isTrue();
		assertThat(Documento.valido("529.982.247-26")).isFalse();
		assertThat(Documento.valido("11.222.333/0001-82")).isFalse();
		assertThat(Documento.valido("111.111.111-11")).isFalse();
		assertThat(Documento.valido("123")).isFalse();
	}
}
