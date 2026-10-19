package br.com.crispimcampos.gestao.relatorios;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PdfRendererTest {

	@Test
	void geraPdfValidoComAcentos() {
		String html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>"
				+ "<style>body{font-family:sans-serif}</style></head><body><h1>Relatório de audiências</h1><p>Ação, situação e próximos passos.</p></body></html>";
		byte[] pdf = new PdfRenderer().renderizar(html);
		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		assertThat(pdf.length).isGreaterThan(500);
	}
}
