package br.com.crispimcampos.gestao.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.importacao.PlanilhaFinanceira.Situacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanilhaFinanceiraTest {

	PlanilhaFinanceira.Resultado resultado = PlanilhaFinanceira.interpretar(XlsxLeitor.ler(XlsxFicticio.planilhaModelo()));

	@Test
	void leitorRecusaArquivoQueNaoEXlsx() {
		assertThatThrownBy(() -> XlsxLeitor.ler("nao e zip".getBytes())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void leContratosComAreaSugeridaEAvisos() {
		var c = resultado.contratos();
		assertThat(c).hasSize(3);
		assertThat(c.get(0).cnj()).isEqualTo(XlsxFicticio.CNJ_MODELO.replaceAll("\\D", ""));
		assertThat(c.get(0).areasSugeridas()).containsExactly(AreaAtuacao.FAMILIA);
		assertThat(c.get(0).valorTotal()).isEqualByComparingTo("6000");
		assertThat(c.get(0).plano().parcelas()).isEqualTo(24);
		assertThat(c.get(0).plano().entrada()).isEqualByComparingTo("1200");
		assertThat(c.get(1).percentualExito()).isEqualByComparingTo("30");
		assertThat(c.get(1).plano().aoFinalDoProcesso()).isTrue();
		assertThat(c.get(2).areasSugeridas()).isEmpty();
		assertThat(c.get(2).avisos()).anyMatch(a -> a.contains("Area de atuacao nao identificada"));
		assertThat(c.get(2).avisos()).anyMatch(a -> a.contains("texto junto do numero"));
		assertThat(c.get(2).valorTotal()).isEqualByComparingTo("100");
	}

	@Test
	void leParcelasComStatusDatasEValoresSujos() {
		var p = resultado.parcelas();
		assertThat(p).hasSize(6);
		var ana = p.get(0);
		assertThat(ana.vencimento()).isEqualTo(LocalDate.of(2026, 4, 10));
		assertThat(ana.numero()).isEqualTo(1);
		assertThat(ana.totalParcelas()).isEqualTo(24);
		assertThat(ana.situacao()).isEqualTo(Situacao.PAGA);
		var bruno = p.get(1);
		assertThat(bruno.valor()).isEqualByComparingTo("400.00");
		assertThat(bruno.vencimento()).isNull();
		assertThat(bruno.avisos()).anyMatch(a -> a.contains("nao e uma data"));
		assertThat(bruno.situacao()).isEqualTo(Situacao.PARCIAL);
		assertThat(p.get(2).situacao()).isEqualTo(Situacao.CANCELADA);
		assertThat(p.get(3).numero()).isZero();
		assertThat(p.get(3).situacao()).isEqualTo(Situacao.ABERTA);
	}

	@Test
	void ignoraTotaisExtrasESinalizaAbaDesconhecida() {
		assertThat(resultado.ignorados()).extracting(PlanilhaFinanceira.Ignorado::motivo)
				.anyMatch(m -> m.contains("Total do mes"))
				.anyMatch(m -> m.contains("Linha de total"))
				.anyMatch(m -> m.contains("extra sem cliente"))
				.anyMatch(m -> m.contains("despesa incompleta"));
		assertThat(resultado.abasNaoReconhecidas()).containsExactly("Aba Estranha");
	}

	@Test
	void leDespesasDoEscritorio() {
		assertThat(resultado.despesas()).hasSize(1);
		var d = resultado.despesas().get(0);
		assertThat(d.valor()).isEqualByComparingTo(new BigDecimal("41.99"));
		assertThat(d.categoria()).isEqualTo("LEMBRANÇA");
		assertThat(d.referencia()).contains("MARÇO");
	}

	@Test
	void valoresEDatasDeTextoLivre() {
		assertThat(Texto.valor("R$ 1.200,50")).isEqualByComparingTo("1200.50");
		assertThat(Texto.valor("400.0")).isEqualByComparingTo("400");
		assertThat(Texto.valor("XXX")).isNull();
		assertThat(Texto.data("05/04/2026")).isEqualTo(LocalDate.of(2026, 4, 5));
		assertThat(Texto.data("1ª QUARTA-FEIRA DO MÊS")).isNull();
		assertThat(Texto.normalizar("  Ação   Cível ")).isEqualTo("ACAO CIVEL");
	}
}
