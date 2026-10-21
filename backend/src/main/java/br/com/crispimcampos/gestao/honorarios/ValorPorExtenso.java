package br.com.crispimcampos.gestao.honorarios;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Valor em reais por extenso (ex.: "seis mil reais e cinquenta centavos"), para o texto do contrato. Ate 999.999.999,99. */
public final class ValorPorExtenso {

	private static final String[] UNIDADES = {"zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze", "doze", "treze",
			"quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove"};
	private static final String[] DEZENAS = {"", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa"};
	private static final String[] CENTENAS = {"", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos"};

	private ValorPorExtenso() {
	}

	public static String de(BigDecimal valor) {
		BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP);
		if (v.signum() < 0 || v.compareTo(new BigDecimal("999999999.99")) > 0) {
			throw new IllegalArgumentException("Valor fora do intervalo");
		}
		long reais = v.toBigInteger().longValue();
		int centavos = v.remainder(BigDecimal.ONE).movePointRight(2).intValue();
		StringBuilder s = new StringBuilder();
		if (reais > 0 || centavos == 0) {
			s.append(inteiro(reais)).append(reais == 1 ? " real" : (reais != 0 && reais % 1_000_000 == 0 ? " de reais" : " reais"));
		}
		if (centavos > 0) {
			if (s.length() > 0) {
				s.append(" e ");
			}
			s.append(centenas(centavos)).append(centavos == 1 ? " centavo" : " centavos");
		}
		return s.toString();
	}

	static String inteiro(long n) {
		if (n == 0) {
			return "zero";
		}
		java.util.List<long[]> partes = new java.util.ArrayList<>(); // {valor, tipo: 2 = milhoes, 1 = milhares, 0 = resto}
		if (n / 1_000_000 > 0) {
			partes.add(new long[] {n / 1_000_000, 2});
		}
		if ((n / 1000) % 1000 > 0) {
			partes.add(new long[] {(n / 1000) % 1000, 1});
		}
		if (n % 1000 > 0) {
			partes.add(new long[] {n % 1000, 0});
		}
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < partes.size(); i++) {
			long v = partes.get(i)[0];
			long tipo = partes.get(i)[1];
			if (i > 0) {
				// "e" so antes da ultima parte quando ela for menor que 100 ou centena exata
				boolean ultima = i == partes.size() - 1;
				s.append(ultima && (v < 100 || v % 100 == 0) ? " e " : " ");
			}
			if (tipo == 2) {
				s.append(centenas((int) v)).append(v == 1 ? " milhão" : " milhões");
			} else if (tipo == 1) {
				s.append(v == 1 ? "mil" : centenas((int) v) + " mil");
			} else {
				s.append(centenas((int) v));
			}
		}
		return s.toString();
	}

	private static String centenas(int n) {
		if (n == 100) {
			return "cem";
		}
		StringBuilder s = new StringBuilder();
		int c = n / 100;
		int r = n % 100;
		if (c > 0) {
			s.append(CENTENAS[c]);
		}
		if (r > 0) {
			if (c > 0) {
				s.append(" e ");
			}
			if (r < 20) {
				s.append(UNIDADES[r]);
			} else {
				s.append(DEZENAS[r / 10]);
				if (r % 10 > 0) {
					s.append(" e ").append(UNIDADES[r % 10]);
				}
			}
		}
		return s.toString();
	}
}
