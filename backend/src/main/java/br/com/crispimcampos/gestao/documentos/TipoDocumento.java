package br.com.crispimcampos.gestao.documentos;

/** Tipos de documento da pasta do cliente. Contrato de honorarios traz valores: sempre restrito as socias. */
public enum TipoDocumento {
	CONTRATO_HONORARIOS("Contrato de honorários"), PROCURACAO("Procuração"), DOCUMENTO_PESSOAL("Documento pessoal"), OUTRO("Outro");

	private final String rotulo;

	TipoDocumento(String rotulo) {
		this.rotulo = rotulo;
	}

	public String rotulo() {
		return rotulo;
	}

	/** Palpite pelo nome do arquivo; o que nao se reconhece fica OUTRO. */
	public static TipoDocumento pelNome(String nome) {
		String n = java.text.Normalizer.normalize(nome == null ? "" : nome, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
		if (n.contains("procura")) {
			return PROCURACAO;
		}
		if (n.contains("contrato") || n.contains("honorario")) {
			return CONTRATO_HONORARIOS;
		}
		if (n.matches(".*\\b(rg|cpf|cnh|identidade|certidao|comprovante|passaporte|titulo de eleitor)\\b.*")) {
			return DOCUMENTO_PESSOAL;
		}
		return OUTRO;
	}
}
