package br.com.crispimcampos.gestao.documentos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentoDriveSugestaoTest {

	@Test
	void reconheceONumeroCnjComOuSemMascaraNoNomeOuNaPasta() {
		var m = DocumentoDriveService.CNJ.matcher("Processos / 0000001-31.2025.8.07.0001 / Peticao inicial.pdf");
		assertThat(m.find()).isTrue();
		assertThat(m.group(1) + m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6)).isEqualTo("00000013120258070001");
		var puro = DocumentoDriveService.CNJ.matcher("Inicial 00000013120258070001.docx");
		assertThat(puro.find()).isTrue();
	}

	@Test
	void formataOCnjParaNomeDePasta() {
		assertThat(DriveMigracaoService.formatar("00000013120258070001")).isEqualTo("0000001-31.2025.8.07.0001");
		assertThat(DriveMigracaoService.formatar("123")).isEqualTo("123");
	}

	@Test
	void tipoSugeridoPeloNomeDoArquivo() {
		assertThat(TipoDocumento.pelNome("Procuração Maria.pdf")).isEqualTo(TipoDocumento.PROCURACAO);
		assertThat(TipoDocumento.pelNome("Contrato de honorários.docx")).isEqualTo(TipoDocumento.CONTRATO_HONORARIOS);
		assertThat(TipoDocumento.pelNome("Réplica.pdf")).isEqualTo(TipoDocumento.OUTRO);
	}
}
