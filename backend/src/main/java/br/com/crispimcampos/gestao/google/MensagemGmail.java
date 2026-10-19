package br.com.crispimcampos.gestao.google;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Mensagem do Gmail ja reduzida ao que a captura usa. */
public record MensagemGmail(String id, String assunto, String remetente, LocalDate data, String texto) {

	private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

	@SuppressWarnings("unchecked")
	static MensagemGmail de(Map<String, Object> m) {
		Map<String, Object> payload = m.get("payload") instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of();
		String assunto = "";
		String remetente = "";
		if (payload.get("headers") instanceof List<?> hs) {
			for (Object h : hs) {
				if (h instanceof Map<?, ?> hm) {
					String nome = String.valueOf(hm.get("name"));
					if ("Subject".equalsIgnoreCase(nome)) {
						assunto = String.valueOf(hm.get("value"));
					} else if ("From".equalsIgnoreCase(nome)) {
						remetente = String.valueOf(hm.get("value"));
					}
				}
			}
		}
		long ms = m.get("internalDate") == null ? System.currentTimeMillis() : Long.parseLong(String.valueOf(m.get("internalDate")));
		String texto = extrair(payload, "text/plain");
		if (texto.isBlank()) {
			texto = html(extrair(payload, "text/html"));
		}
		return new MensagemGmail(String.valueOf(m.get("id")), assunto, remetente,
				Instant.ofEpochMilli(ms).atZone(SP).toLocalDate(), texto.strip());
	}

	@SuppressWarnings("unchecked")
	private static String extrair(Map<String, Object> parte, String tipo) {
		if (tipo.equals(parte.get("mimeType")) && parte.get("body") instanceof Map<?, ?> b && b.get("data") != null) {
			try {
				return new String(Base64.getUrlDecoder().decode(String.valueOf(b.get("data"))), StandardCharsets.UTF_8);
			} catch (IllegalArgumentException e) {
				return "";
			}
		}
		if (parte.get("parts") instanceof List<?> ps) {
			for (Object p : ps) {
				if (p instanceof Map<?, ?> pm) {
					String t = extrair((Map<String, Object>) pm, tipo);
					if (!t.isBlank()) {
						return t;
					}
				}
			}
		}
		return "";
	}

	static String html(String h) {
		return h.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("(?s)<[^>]+>", " ")
				.replace("&nbsp;", " ").replace("&amp;", "&").replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\s*\\n\\s*", "\n");
	}
}
