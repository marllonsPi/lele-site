package br.com.crispimcampos.gestao.contas;

public enum Perfil {
	SOCIA, ADVOGADO, ASSISTENTE, ESTAGIARIO;

	public String papel() {
		return "ROLE_" + name();
	}
}
