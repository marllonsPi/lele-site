package br.com.crispimcampos.gestao.honorarios;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.crispimcampos.gestao.honorarios.SugestaoHonorarios.Servico;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SugestaoHonorariosTest {

	static Servico s(String tipo, String min, String tabela, String sugerido, String exito) {
		return new Servico(tipo, "FAMILIA", tipo, null, 1L, 2L, min == null ? null : new BigDecimal(min), tabela == null ? null : new BigDecimal(tabela),
				sugerido == null ? null : new BigDecimal(sugerido), exito, null, null);
	}

	@Test
	void quadroTrazMinimoOabValorDoEscritorioEOpcoesDeParcelamento() {
		var q = SugestaoHonorarios.calcular(List.of(s("Divorcio", "15245.20", "18294.24", "16464.82", null)), BigDecimal.ZERO, new BigDecimal("1.2"));
		assertThat(q.minimoOabTotal()).isEqualByComparingTo("15245.20");
		assertThat(q.valorTabelaTotal()).isEqualByComparingTo("18294.24");
		assertThat(q.valorSugeridoTotal()).isEqualByComparingTo("16464.82");
		assertThat(q.opcoes()).extracting(o -> o.rotulo()).first().asString().startsWith("A vista");
		var avista = q.opcoes().get(0);
		assertThat(avista.total()).isEqualByComparingTo("16464.82");
		var seis = q.opcoes().stream().filter(o -> o.parcelas() == 6).findFirst().orElseThrow();
		assertThat(seis.total()).isEqualByComparingTo("18294.24"); // parcelado sobre o valor de tabela
		assertThat(seis.entrada()).isEqualByComparingTo("5488.27");
		assertThat(seis.entrada().add(seis.valorParcela().multiply(BigDecimal.valueOf(5))).add(seis.ultimaParcela())).as("a soma fecha no total").isEqualByComparingTo("18294.24");
		assertThat(q.opcoes()).allSatisfy(o -> assertThat(o.total()).isGreaterThanOrEqualTo(q.minimoOabTotal()));
	}

	@Test
	void valorSugeridoNuncaFicaAbaixoDoMinimoDaOab() {
		var q = SugestaoHonorarios.calcular(List.of(s("X", "1000.00", "1100.00", "900.00", null)), BigDecimal.ZERO, null);
		assertThat(q.valorSugeridoTotal()).isEqualByComparingTo("1000.00");
		assertThat(q.linhas().get(0).avisos()).anyMatch(a -> a.contains("elevado ao minimo"));
	}

	@Test
	void semItemNaTabelaDoEscritorioUsaOFatorSobreOMinimo() {
		var q = SugestaoHonorarios.calcular(List.of(s("Y", "1000.00", null, null, null)), BigDecimal.ZERO, new BigDecimal("1.2"));
		assertThat(q.valorSugeridoTotal()).isEqualByComparingTo("1200.00");
		assertThat(q.linhas().get(0).avisos()).anyMatch(a -> a.contains("Sem item na tabela do escritorio"));
	}

	@Test
	void servicoSoDeExitoNaoTemValorFixoMasMostraOPercentual() {
		var q = SugestaoHonorarios.calcular(List.of(s("Reclamacao", null, null, null, "35% a 40% sobre a condenacao")), BigDecimal.ZERO, new BigDecimal("1.2"));
		assertThat(q.valorSugeridoTotal()).isZero();
		assertThat(q.opcoes()).isEmpty();
		assertThat(q.exito()).containsExactly("Reclamacao: 35% a 40% sobre a condenacao");
		assertThat(q.linhas().get(0).avisos()).anyMatch(a -> a.contains("Sem valor fixo"));
	}

	@Test
	void somaVariosServicosComDescontoPorPacoteLimitadoAoMinimo() {
		var servicos = List.of(s("A", "10000.00", "12000.00", "10800.00", null), s("B", "5000.00", "6000.00", "5400.00", null));
		var q = SugestaoHonorarios.calcular(servicos, new BigDecimal("0.05"), new BigDecimal("1.2"));
		assertThat(q.minimoOabTotal()).isEqualByComparingTo("15000.00");
		assertThat(q.valorSugeridoTotal()).isEqualByComparingTo("15390.00").as("16200 com 5% de pacote");
		assertThat(q.descontoPacoteAplicado()).isEqualByComparingTo("810.00");
		var grande = SugestaoHonorarios.calcular(servicos, new BigDecimal("0.50"), new BigDecimal("1.2"));
		assertThat(grande.valorSugeridoTotal()).isEqualByComparingTo("15000.00");
		assertThat(grande.avisos()).anyMatch(a -> a.contains("limitado pelo minimo da OAB"));
		var um = SugestaoHonorarios.calcular(List.of(servicos.get(0)), new BigDecimal("0.10"), null);
		assertThat(um.valorSugeridoTotal()).as("pacote so com mais de um servico").isEqualByComparingTo("10800.00");
	}
}
