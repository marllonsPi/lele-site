package br.com.crispimcampos.gestao.google;

/** Falha do fluxo OAuth ou da conta Google. A mensagem e segura para exibir (nunca traz token nem e-mail). */
public class ConexaoGoogleException extends RuntimeException {

	private final boolean expirada;

	public ConexaoGoogleException(String mensagem) {
		this(mensagem, false);
	}

	/** {@code expirada}: o Google recusou o refresh token (revogado ou vencido); a pessoa precisa reconectar. */
	public ConexaoGoogleException(String mensagem, boolean expirada) {
		super(mensagem);
		this.expirada = expirada;
	}

	public boolean expirada() {
		return expirada;
	}
}
