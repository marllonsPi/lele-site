package br.com.crispimcampos.gestao.agenda;

import java.util.Locale;
import java.util.Map;

/** Tipos de peca aceitos: extensao conhecida E assinatura (primeiros bytes) coerente com ela. */
final class TipoArquivo {

	private static final byte[] PDF = {'%', 'P', 'D', 'F'};
	private static final byte[] ZIP = {'P', 'K', 3, 4};
	private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
	private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

	/** Extensao -> assinatura; null = texto puro, sem assinatura. */
	private static final Map<String, byte[]> ACEITOS = Map.ofEntries(
			Map.entry("pdf", PDF), Map.entry("docx", ZIP), Map.entry("xlsx", ZIP), Map.entry("odt", ZIP),
			Map.entry("doc", OLE), Map.entry("xls", OLE), Map.entry("png", PNG), Map.entry("jpg", JPG),
			Map.entry("jpeg", JPG));

	private TipoArquivo() {
	}

	static String extensao(String nome) {
		int i = nome.lastIndexOf('.');
		return i < 0 ? "" : nome.substring(i + 1).toLowerCase(Locale.ROOT);
	}

	static boolean aceito(String nome, byte[] conteudo) {
		byte[] assinatura = ACEITOS.get(extensao(nome));
		if (assinatura == null || conteudo.length < assinatura.length) {
			return false;
		}
		for (int i = 0; i < assinatura.length; i++) {
			if (conteudo[i] != assinatura[i]) {
				return false;
			}
		}
		return true;
	}
}
