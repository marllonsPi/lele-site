package br.com.crispimcampos.gestao.cadastros;

import java.math.BigInteger;

/** Numero unico CNJ (NNNNNNN-DD.AAAA.J.TR.OOOO): normaliza para 20 digitos e confere o digito verificador (mod 97). */
public final class NumeroCnj {

	private static final BigInteger NOVENTA_E_OITO = BigInteger.valueOf(98);
	private static final BigInteger NOVENTA_E_SETE = BigInteger.valueOf(97);

	private NumeroCnj() {
	}

	public static String normalizar(String texto) {
		return texto == null ? "" : texto.replaceAll("\\D", "");
	}

	public static boolean valido(String texto) {
		String n = normalizar(texto);
		if (n.length() != 20) {
			return false;
		}
		String sequencial = n.substring(0, 7);
		String dv = n.substring(7, 9);
		String resto = n.substring(9);
		return calcularDv(sequencial, resto).equals(dv);
	}

	/** Digito verificador de um numero (sequencial de 7 digitos + ano/orgao/tribunal/origem de 11). */
	public static String calcularDv(String sequencial, String anoOrgaoOrigem) {
		BigInteger base = new BigInteger(sequencial + anoOrgaoOrigem + "00");
		int dv = NOVENTA_E_OITO.subtract(base.mod(NOVENTA_E_SETE)).intValue();
		return String.format("%02d", dv);
	}

	public static String formatar(String n) {
		return n.substring(0, 7) + "-" + n.substring(7, 9) + "." + n.substring(9, 13) + "." + n.charAt(13) + "."
				+ n.substring(14, 16) + "." + n.substring(16);
	}
}
