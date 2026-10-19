package br.com.crispimcampos.gestao.relatorios;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Component;

/** HTML (XHTML estrito) para PDF. Usa as fontes padrao do PDF (suficientes para portugues). */
@Component
class PdfRenderer {

	byte[] renderizar(String xhtml) {
		try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			PdfRendererBuilder b = new PdfRendererBuilder();
			b.useFastMode();
			b.withHtmlContent(xhtml, null);
			b.toStream(saida);
			b.run();
			return saida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao gerar o PDF", e);
		}
	}
}
