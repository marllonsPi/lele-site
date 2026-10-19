package br.com.crispimcampos.gestao.cadastros;

/** Tambem usada quando o recurso existe mas o usuario nao o ve: nao revela a existencia (sem IDOR). */
public class CadastroNaoEncontradoException extends RuntimeException {

	public CadastroNaoEncontradoException(String mensagem) {
		super(mensagem);
	}
}
