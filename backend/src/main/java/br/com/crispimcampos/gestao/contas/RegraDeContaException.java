package br.com.crispimcampos.gestao.contas;

/** Violacao de regra de negocio na gestao de contas; vira 409/422 na API. */
public class RegraDeContaException extends RuntimeException {

	private final boolean conflito;

	RegraDeContaException(String mensagem, boolean conflito) {
		super(mensagem);
		this.conflito = conflito;
	}

	public boolean isConflito() {
		return conflito;
	}
}
