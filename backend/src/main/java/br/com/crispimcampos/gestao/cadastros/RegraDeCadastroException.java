package br.com.crispimcampos.gestao.cadastros;

public class RegraDeCadastroException extends RuntimeException {

	private final boolean conflito;

	RegraDeCadastroException(String mensagem, boolean conflito) {
		super(mensagem);
		this.conflito = conflito;
	}

	boolean isConflito() {
		return conflito;
	}
}
