package br.com.crispimcampos.gestao.honorarios;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;

/** Identifica um servico (area + tipo + fase) de forma estavel, para parear o valor da OAB com o da tabela do escritorio. */
public final class ChaveServico {

	private ChaveServico() {
	}

	public static String normalizar(String s) {
		if (s == null) {
			return "";
		}
		String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
		return n.replaceAll("[^A-Z0-9%]+", " ").trim();
	}

	public static String de(String area, String tipoServico, String fase) {
		String base = normalizar(area) + "|" + normalizar(tipoServico) + "|" + normalizar(fase);
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
