package br.com.crispimcampos.gestao.cadastros;

/** Areas de atuacao do escritorio: cliente pode ter varias; processo e contrato, uma. */
public enum AreaAtuacao {
	TRABALHISTA("Trabalhista"),
	PREVIDENCIARIO("Previdenciário"),
	FAMILIA("Família"),
	SUCESSAO("Sucessão"),
	IMOBILIARIO("Imobiliário"),
	CONDOMINIAL("Condominial"),
	MILITAR("Militar"),
	CRIMINAL("Criminal"),
	ADMINISTRATIVO("Administrativo"),
	CIVEL("Cível");

	private final String rotulo;

	AreaAtuacao(String rotulo) {
		this.rotulo = rotulo;
	}

	public String rotulo() {
		return rotulo;
	}
}
