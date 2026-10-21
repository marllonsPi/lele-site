package br.com.crispimcampos.gestao.importacao;

import java.util.List;

/** A previa tem pendencias (area, vencimento...) e nao pode ser confirmada ainda. */
public class PendenciaDeImportacaoException extends RuntimeException {

	private final List<String> pendencias;

	public PendenciaDeImportacaoException(List<String> pendencias) {
		super("Resolva as pendencias antes de confirmar (" + pendencias.size() + ")");
		this.pendencias = List.copyOf(pendencias);
	}

	public List<String> pendencias() {
		return pendencias;
	}
}
