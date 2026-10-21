package br.com.crispimcampos.gestao.importacao;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizacao de texto e de valores "sujos" de planilha (R$ 1.200,50; 46093.0; "100 (TMP)"). */
final class Texto {

	private static final Pattern MOEDA = Pattern.compile("-?\\d[\\d.]*(,\\d+)?|-?\\d+\\.\\d+");
	private static final LocalDate EPOCA_EXCEL = LocalDate.of(1899, 12, 30);

	private Texto() {
	}

	/** Maiusculas, sem acento, espacos colapsados: chave de comparacao de nomes e rotulos. */
	static String normalizar(String s) {
		if (s == null) {
			return "";
		}
		String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return n.toUpperCase().replaceAll("\\s+", " ").trim();
	}

	/** Primeiro numero do texto como valor monetario; null se nao houver. Aceita "R$ 1.200,50", "400.0", "100 (TMP)". */
	static BigDecimal valor(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		Matcher m = MOEDA.matcher(s.replace("R$", "").trim());
		if (!m.find()) {
			return null;
		}
		String t = m.group();
		if (t.contains(",")) {
			t = t.replace(".", "").replace(',', '.');
		} else if (t.chars().filter(c -> c == '.').count() > 1) {
			t = t.replace(".", "");
		}
		try {
			return new BigDecimal(t).setScale(2, java.math.RoundingMode.HALF_UP);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Valor somente quando o texto inteiro e um numero/moeda (sem observacoes junto). */
	static boolean somenteNumero(String s) {
		return s != null && s.replace("R$", "").trim().matches("-?[\\d.,]+");
	}

	/** Numero serial do Excel (46093.0) ou data ISO/BR; null se nao for data. */
	static LocalDate data(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		String t = s.trim();
		try {
			double d = Double.parseDouble(t);
			if (d > 20000 && d < 80000) {
				return EPOCA_EXCEL.plusDays((long) d);
			}
			return null;
		} catch (NumberFormatException e) {
			// segue para formatos de texto
		}
		Matcher br = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matcher(t);
		if (br.matches()) {
			try {
				return LocalDate.of(Integer.parseInt(br.group(3)), Integer.parseInt(br.group(2)), Integer.parseInt(br.group(1)));
			} catch (java.time.DateTimeException e) {
				return null;
			}
		}
		Matcher iso = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})").matcher(t);
		if (iso.find()) {
			try {
				return LocalDate.parse(iso.group());
			} catch (java.time.DateTimeException e) {
				return null;
			}
		}
		return null;
	}

	static String hash(String... partes) {
		try {
			var md = java.security.MessageDigest.getInstance("SHA-256");
			byte[] h = md.digest(String.join("\u001f", partes).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(h);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
