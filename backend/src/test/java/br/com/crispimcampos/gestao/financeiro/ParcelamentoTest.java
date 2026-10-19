package br.com.crispimcampos.gestao.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ParcelamentoTest {

	@Test
	void somaDasParcelasEsempreOTotalComCentavosNaUltima() {
		var p = Parcelamento.dividir(new BigDecimal("1000.00"), 3, LocalDate.parse("2026-11-10"));
		assertThat(p).extracting(Parcelamento.ParcelaPrevista::valor).containsExactly(new BigDecimal("333.33"), new BigDecimal("333.33"), new BigDecimal("333.34"));
		assertThat(p.stream().map(Parcelamento.ParcelaPrevista::valor).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1000.00");
	}

	@Test
	void somaBateParaVariosTotaisENumerosDeParcelas() {
		for (String total : new String[] {"0.10", "99.99", "12345.67", "1000000.01"}) {
			for (int n : new int[] {1, 2, 7, 12, 36}) {
				BigDecimal valor = new BigDecimal(total);
				if (valor.compareTo(BigDecimal.valueOf(n).movePointLeft(2)) < 0) {
					continue;
				}
				var p = Parcelamento.dividir(valor, n, LocalDate.parse("2026-01-15"));
				assertThat(p).hasSize(n);
				assertThat(p.stream().map(Parcelamento.ParcelaPrevista::valor).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(valor);
				assertThat(p).allSatisfy(x -> assertThat(x.valor().signum()).isPositive());
			}
		}
	}

	@Test
	void vencimentosMensaisComFimDeMes() {
		var p = Parcelamento.dividir(new BigDecimal("300"), 3, LocalDate.parse("2026-01-31"));
		assertThat(p).extracting(Parcelamento.ParcelaPrevista::vencimento)
				.containsExactly(LocalDate.parse("2026-01-31"), LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-31"));
	}

	@Test
	void recusaEntradasInvalidas() {
		assertThatThrownBy(() -> Parcelamento.dividir(BigDecimal.ZERO, 3, LocalDate.now())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Parcelamento.dividir(BigDecimal.TEN, 0, LocalDate.now())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Parcelamento.dividir(BigDecimal.TEN, 121, LocalDate.now())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Parcelamento.dividir(new BigDecimal("0.01"), 2, LocalDate.now())).isInstanceOf(IllegalArgumentException.class);
	}
}
