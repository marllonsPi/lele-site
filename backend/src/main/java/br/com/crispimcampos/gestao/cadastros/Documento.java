package br.com.crispimcampos.gestao.cadastros;

/** CPF (11) ou CNPJ (14) com conferencia dos digitos verificadores. */
public final class Documento {

	private Documento() {
	}

	public static String normalizar(String texto) {
		return texto == null ? "" : texto.replaceAll("\\D", "");
	}

	public static boolean valido(String texto) {
		String d = normalizar(texto);
		if (d.chars().distinct().count() == 1) {
			return false;
		}
		return switch (d.length()) {
			case 11 -> digito(d, 9, 10) == d.charAt(9) - '0' && digito(d, 10, 11) == d.charAt(10) - '0';
			case 14 -> cnpj(d, 12) == d.charAt(12) - '0' && cnpj(d, 13) == d.charAt(13) - '0';
			default -> false;
		};
	}

	private static int digito(String d, int n, int pesoInicial) {
		int soma = 0;
		for (int i = 0; i < n; i++) {
			soma += (d.charAt(i) - '0') * (pesoInicial - i);
		}
		int r = (soma * 10) % 11;
		return r == 10 ? 0 : r;
	}

	private static int cnpj(String d, int n) {
		int soma = 0;
		int peso = n - 7;
		for (int i = 0; i < n; i++) {
			soma += (d.charAt(i) - '0') * peso--;
			if (peso < 2) {
				peso = 9;
			}
		}
		int r = soma % 11;
		return r < 2 ? 0 : 11 - r;
	}
}
