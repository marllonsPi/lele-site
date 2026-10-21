package br.com.crispimcampos.gestao.honorarios;

/** Proposta inexistente ou fora do alcance de quem pede (responde como inexistente). */
public class PropostaNaoEncontradaException extends RuntimeException {

	public PropostaNaoEncontradaException(String mensagem) {
		super(mensagem);
	}
}
