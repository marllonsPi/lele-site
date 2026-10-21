package br.com.crispimcampos.gestao.captura;

import tools.jackson.databind.json.JsonMapper;

final class MetadadosJson {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private MetadadosJson() {
	}

	static String escrever(MetadadosDjen m) {
		try {
			return MAPPER.writeValueAsString(m);
		} catch (RuntimeException e) {
			return null; // metadado e extra: nunca derruba a captura
		}
	}

	static MetadadosDjen ler(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readValue(json, MetadadosDjen.class);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
