package br.com.crispimcampos.gestao.agenda;

/** Tambem quando o item existe mas o usuario nao o ve (sem IDOR). */
public class AgendaNaoEncontradaException extends RuntimeException {

	AgendaNaoEncontradaException(String mensagem) {
		super(mensagem);
	}
}
