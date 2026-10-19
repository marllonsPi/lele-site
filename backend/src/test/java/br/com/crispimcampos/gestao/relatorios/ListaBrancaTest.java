package br.com.crispimcampos.gestao.relatorios;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Invariante 6: o relatorio ao cliente so tem campos da lista branca. Se alguem acrescentar um campo (ex.: observacoes,
 * prazoInterno, responsavel), este teste falha e obriga a decisao consciente e o registro em decisions.md.
 */
class ListaBrancaTest {

	static Set<String> campos(Class<?> tipo) {
		Set<String> nomes = new TreeSet<>();
		for (RecordComponent c : tipo.getRecordComponents()) {
			nomes.add(c.getName());
		}
		return nomes;
	}

	@Test
	void conteudoDoRelatorioTemExatamenteOsCamposPermitidos() {
		assertThat(campos(ConteudoCliente.class)).containsExactlyInAnyOrder("clienteNome", "periodoInicio", "periodoFim", "processos", "comentarios", "financeiro");
		assertThat(campos(ConteudoCliente.ProcessoRel.class)).containsExactlyInAnyOrder("numero", "varaComarca", "tribunal", "tipoAcao", "situacao",
				"partes", "movimentacoes", "feitos", "proximosPassos");
		assertThat(campos(ConteudoCliente.Item.class)).containsExactlyInAnyOrder("data", "texto");
		assertThat(campos(ConteudoCliente.FinanceiroRel.class)).containsExactlyInAnyOrder("parcelasPagas", "parcelasAVencer");
	}

	@Test
	void dadosDaAgendaParaOClienteNaoTemCamposInternos() {
		assertThat(campos(br.com.crispimcampos.gestao.agenda.DadosParaCliente.Feito.class)).containsExactlyInAnyOrder("tipo", "data");
		assertThat(campos(br.com.crispimcampos.gestao.agenda.DadosParaCliente.ProximoPasso.class))
				.containsExactlyInAnyOrder("tipo", "data", "hora", "modalidade", "localOuLink");
	}

	@Test
	void htmlEscapaEGeraPdfValidoComTimbre() {
		var timbre = new HtmlRelatorios.Timbre("Escritório de Teste", "", "", null);
		var conteudo = new ConteudoCliente("Cliente <b>X</b> & Cia", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"),
				List.of(new ConteudoCliente.ProcessoRel("0001234-56.2025.8.07.0001", "1ª Vara", "TJXX", "Procedimento Comum", "Em andamento",
						List.of("Autor", "Réu"), List.of(new ConteudoCliente.Item(LocalDate.parse("2026-09-10"), "Juntada de documento — teste")),
						List.of(), List.of())),
				"Linha 1\nLinha 2", new ConteudoCliente.FinanceiroRel(new BigDecimal("1000"), new BigDecimal("2000")));
		String html = HtmlRelatorios.cliente(timbre, conteudo);
		assertThat(html).doesNotContain("<b>X</b>").contains("&lt;b&gt;X&lt;/b&gt;").contains("Linha 1<br/>Linha 2");
		byte[] pdf = new PdfRenderer().renderizar(html);
		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
	}
}
