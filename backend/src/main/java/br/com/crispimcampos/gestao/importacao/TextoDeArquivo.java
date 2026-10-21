package br.com.crispimcampos.gestao.importacao;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Texto de um arquivo (PDF, DOCX, TXT) para extrair dados de contratos. Limita paginas e tamanho; nunca executa o conteudo. */
final class TextoDeArquivo {

	static final int MAXIMO_CARACTERES = 200_000;
	static final int MAXIMO_PAGINAS = 40;

	private TextoDeArquivo() {
	}

	/** null quando o formato nao e suportado ou o arquivo esta ilegivel (ex.: PDF escaneado sem texto). */
	static String ler(String nome, byte[] conteudo) {
		String n = nome == null ? "" : nome.toLowerCase();
		try {
			if (n.endsWith(".pdf")) {
				return pdf(conteudo);
			}
			if (n.endsWith(".docx")) {
				return docx(conteudo);
			}
			if (n.endsWith(".txt")) {
				return limitar(new String(conteudo, StandardCharsets.UTF_8));
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
		return null;
	}

	private static String pdf(byte[] b) throws IOException {
		try (PDDocument doc = PDDocument.load(b)) {
			PDFTextStripper s = new PDFTextStripper();
			s.setEndPage(MAXIMO_PAGINAS);
			return limitar(s.getText(doc));
		}
	}

	private static String docx(byte[] b) throws IOException {
		try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b))) {
			ZipEntry e;
			while ((e = z.getNextEntry()) != null) {
				if (e.getName().equals("word/document.xml")) {
					String xml = new String(z.readNBytes(8 * 1024 * 1024), StandardCharsets.UTF_8);
					String texto = xml.replace("</w:p>", "\n").replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
							.replace("&quot;", "\"").replace("&apos;", "'");
					return limitar(texto);
				}
			}
		}
		return null;
	}

	private static String limitar(String s) {
		return s.length() > MAXIMO_CARACTERES ? s.substring(0, MAXIMO_CARACTERES) : s;
	}
}
