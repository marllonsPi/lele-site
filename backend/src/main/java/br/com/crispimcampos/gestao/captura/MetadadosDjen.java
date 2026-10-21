package br.com.crispimcampos.gestao.captura;

import java.util.List;

/** O que a fonte informa alem do texto: serve para cadastrar processo e cliente sem digitacao. */
public record MetadadosDjen(String classe, String orgao, String tipoComunicacao, String link, List<Parte> partes) {

	/** polo: "ATIVO", "PASSIVO" ou null quando a fonte nao informa. */
	public record Parte(String nome, String polo) {
	}

	public MetadadosDjen {
		partes = partes == null ? List.of() : List.copyOf(partes);
	}

	boolean vazio() {
		return classe == null && orgao == null && tipoComunicacao == null && link == null && partes.isEmpty();
	}
}
