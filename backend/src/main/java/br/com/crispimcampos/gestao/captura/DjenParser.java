package br.com.crispimcampos.gestao.captura;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Le a resposta da API publica de comunicacoes do DJEN (verificada em 2026-10-19):
 * {status, message, count, items:[{id, data_disponibilizacao (AAAA-MM-DD), numero_processo (20 digitos), siglaTribunal,
 * texto, hash, ...}]}. Defensivo: item sem id, data ou texto e ignorado e contado, nunca derruba a pagina.
 */
final class DjenParser {

	record Pagina(List<DadosBrutos> itens, int total, int recebidos, int invalidos) {
	}

	private DjenParser() {
	}

	@SuppressWarnings("unchecked")
	static Pagina parse(Map<String, Object> resposta) {
		Object itens = resposta == null ? null : resposta.get("items");
		int total = resposta != null && resposta.get("count") instanceof Number n ? n.intValue() : 0;
		List<DadosBrutos> lista = new ArrayList<>();
		int recebidos = 0;
		int invalidos = 0;
		if (itens instanceof List<?> l) {
			for (Object o : l) {
				recebidos++;
				DadosBrutos d = o instanceof Map<?, ?> m ? item((Map<String, Object>) m) : null;
				if (d == null) {
					invalidos++;
				} else {
					lista.add(d);
				}
			}
		}
		return new Pagina(lista, total, recebidos, invalidos);
	}

	private static DadosBrutos item(Map<String, Object> m) {
		Object id = m.get("id");
		String data = texto(m.get("data_disponibilizacao"));
		String conteudo = texto(m.get("texto"));
		if (id == null || data == null || conteudo == null) {
			return null;
		}
		LocalDate dia;
		try {
			dia = LocalDate.parse(data.length() > 10 ? data.substring(0, 10) : data);
		} catch (DateTimeParseException e) {
			return null;
		}
		String hash = texto(m.get("hash"));
		return new DadosBrutos(Fonte.DJEN, String.valueOf(id), texto(m.get("numero_processo")), texto(m.get("siglaTribunal")),
				dia, conteudo, hash != null ? hash : Hashes.sha256(conteudo), metadados(m));
	}

	private static MetadadosDjen metadados(Map<String, Object> m) {
		List<MetadadosDjen.Parte> partes = new ArrayList<>();
		if (m.get("destinatarios") instanceof List<?> l) {
			for (Object o : l) {
				if (o instanceof Map<?, ?> d && texto(d.get("nome")) != null) {
					String polo = texto(d.get("polo"));
					partes.add(new MetadadosDjen.Parte(limitar(texto(d.get("nome")), 200),
							"A".equalsIgnoreCase(polo) ? "ATIVO" : "P".equalsIgnoreCase(polo) ? "PASSIVO" : null));
				}
			}
		}
		MetadadosDjen md = new MetadadosDjen(limitar(texto(m.get("nomeClasse")), 200), limitar(texto(m.get("nomeOrgao")), 200),
				limitar(texto(m.get("tipoComunicacao")), 100), limitar(texto(m.get("link")), 500), partes);
		return md.vazio() ? null : md;
	}

	private static String limitar(String t, int max) {
		return t == null ? null : t.length() <= max ? t : t.substring(0, max);
	}

	private static String texto(Object o) {
		return o == null ? null : o.toString();
	}
}
