package br.com.crispimcampos.gestao.honorarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ValorPorExtensoTest {

	@Test
	void valoresComuns() {
		assertThat(ValorPorExtenso.de(new BigDecimal("1.00"))).isEqualTo("um real");
		assertThat(ValorPorExtenso.de(new BigDecimal("0.50"))).isEqualTo("cinquenta centavos");
		assertThat(ValorPorExtenso.de(new BigDecimal("1200.00"))).isEqualTo("mil e duzentos reais");
		assertThat(ValorPorExtenso.de(new BigDecimal("6000.50"))).isEqualTo("seis mil reais e cinquenta centavos");
		assertThat(ValorPorExtenso.de(new BigDecimal("15245.20"))).isEqualTo("quinze mil duzentos e quarenta e cinco reais e vinte centavos");
		assertThat(ValorPorExtenso.de(new BigDecimal("100.00"))).isEqualTo("cem reais");
		assertThat(ValorPorExtenso.de(new BigDecimal("2000000.00"))).isEqualTo("dois milhões de reais");
		assertThat(ValorPorExtenso.de(new BigDecimal("101.01"))).isEqualTo("cento e um reais e um centavo");
	}
}
