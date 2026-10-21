package br.com.crispimcampos.gestao.captura;

import java.time.LocalDate;

/** Item normalizado vindo de qualquer fonte, antes de filtro e cruzamento. */
public record DadosBrutos(Fonte fonte, String idExterno, String numeroProcesso, String tribunal, LocalDate dataPublicacao,
		String conteudo, String hash, MetadadosDjen metadados) {

	public DadosBrutos(Fonte fonte, String idExterno, String numeroProcesso, String tribunal, LocalDate dataPublicacao,
			String conteudo, String hash) {
		this(fonte, idExterno, numeroProcesso, tribunal, dataPublicacao, conteudo, hash, null);
	}
}
