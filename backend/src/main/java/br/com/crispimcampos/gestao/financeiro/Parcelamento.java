package br.com.crispimcampos.gestao.financeiro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Divisao de honorarios em parcelas mensais (dominio puro): a soma das parcelas e sempre o total; a diferenca de centavos vai na ultima. */
public final class Parcelamento {

	public record ParcelaPrevista(int numero, LocalDate vencimento, BigDecimal valor) {
	}

	private Parcelamento() {
	}

	public static List<ParcelaPrevista> dividir(BigDecimal total, int parcelas, LocalDate primeiroVencimento) {
		if (total == null || total.signum() <= 0) {
			throw new IllegalArgumentException("O valor total deve ser positivo");
		}
		if (parcelas < 1 || parcelas > 120) {
			throw new IllegalArgumentException("Numero de parcelas invalido (1 a 120)");
		}
		BigDecimal base = total.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
		if (base.signum() == 0) {
			throw new IllegalArgumentException("Valor total pequeno demais para este numero de parcelas");
		}
		List<ParcelaPrevista> lista = new ArrayList<>();
		BigDecimal acumulado = BigDecimal.ZERO;
		for (int i = 1; i <= parcelas; i++) {
			BigDecimal valor = i == parcelas ? total.setScale(2, RoundingMode.HALF_EVEN).subtract(acumulado) : base;
			acumulado = acumulado.add(valor);
			lista.add(new ParcelaPrevista(i, primeiroVencimento.plusMonths(i - 1L), valor));
		}
		return lista;
	}
}
