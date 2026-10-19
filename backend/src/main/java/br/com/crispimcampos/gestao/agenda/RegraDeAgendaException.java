package br.com.crispimcampos.gestao.agenda;

public class RegraDeAgendaException extends RuntimeException {

	private final boolean proibido;

	RegraDeAgendaException(String mensagem, boolean proibido) {
		super(mensagem);
		this.proibido = proibido;
	}

	boolean isProibido() {
		return proibido;
	}
}
