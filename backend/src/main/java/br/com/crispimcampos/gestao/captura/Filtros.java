package br.com.crispimcampos.gestao.captura;

import java.util.Arrays;
import java.util.Locale;

/** Filtro de captura (item 3), dominio puro: ignora por prefixo de tribunal, salvo excecoes. */
final class Filtros {

	private Filtros() {
	}

	/** "TRT-10", "trt 10" e "TRT10" sao o mesmo tribunal. */
	static String normalizar(String tribunal) {
		return tribunal == null ? "" : tribunal.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
	}

	/** true se o tribunal comeca com o prefixo e nao esta na lista de excecoes (separadas por virgula). */
	static boolean ignora(String tribunal, String prefixo, String excecoes) {
		String t = normalizar(tribunal);
		if (t.isEmpty() || !t.startsWith(normalizar(prefixo))) {
			return false;
		}
		return excecoes == null || Arrays.stream(excecoes.split(",")).map(Filtros::normalizar).filter(s -> !s.isEmpty())
				.noneMatch(t::equals);
	}
}
