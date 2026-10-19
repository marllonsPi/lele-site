package br.com.crispimcampos.gestao.cadastros;

/** Tambem usada quando o recurso existe mas o usuario nao o ve: nao revela a existencia (sem IDOR). */
public class CadastroNaoEncontradoException extends RuntimeException {

	CadastroNaoEncontradoException(String mensagem) {
		super(mensagem);
	}
}
