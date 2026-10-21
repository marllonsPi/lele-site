package br.com.crispimcampos.gestao.honorarios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculo puro do quadro de sugestao: minimo da OAB, valor do escritorio, exito e opcoes de parcelamento, para um ou mais
 * servicos. O valor sugerido nunca fica abaixo do minimo da OAB (art. 4º da Tabela). Sem acesso a banco: testavel isoladamente.
 */
public final class SugestaoHonorarios {

	/** Servico ja casado com os valores das duas tabelas (qualquer um pode faltar). */
	public record Servico(String chave, String area, String tipo, String fase, Long itemOabId, Long itemEscritorioId, BigDecimal minimoOab,
			BigDecimal valorTabela, BigDecimal valorSugeridoTabela, String exitoTexto, BigDecimal exitoMin, BigDecimal exitoMax) {
	}

	public record Linha(Servico servico, BigDecimal minimoOab, BigDecimal valorTabela, BigDecimal valorSugerido, List<String> avisos) {
	}

	/** {@code entrada} e {@code parcelas}: 0 parcelas = pagamento a vista. */
	public record OpcaoParcelamento(String rotulo, BigDecimal total, BigDecimal entrada, int parcelas, BigDecimal valorParcela, BigDecimal ultimaParcela) {
	}

	public record Quadro(List<Linha> linhas, BigDecimal minimoOabTotal, BigDecimal valorTabelaTotal, BigDecimal valorSugeridoTotal, BigDecimal descontoPacote,
			BigDecimal descontoPacoteAplicado, List<String> exito, List<OpcaoParcelamento> opcoes, List<String> avisos) {
	}

	static final BigDecimal ENTRADA_PADRAO = new BigDecimal("0.30");
	static final int[] PARCELAS_PADRAO = {3, 6, 10, 12};

	private SugestaoHonorarios() {
	}

	/**
	 * @param descontoPacote desconto por pacote definido pelas socias (0 a 1), aplicado so com mais de um servico e nunca abaixo
	 *                       da soma dos minimos da OAB
	 * @param fatorEscritorio fator sobre o minimo da OAB, usado quando o servico nao tem item na tabela do escritorio (pode ser nulo)
	 */
	public static Quadro calcular(List<Servico> servicos, BigDecimal descontoPacote, BigDecimal fatorEscritorio) {
		List<Linha> linhas = new ArrayList<>();
		List<String> avisos = new ArrayList<>();
		BigDecimal min = BigDecimal.ZERO;
		BigDecimal tabela = BigDecimal.ZERO;
		BigDecimal sugerido = BigDecimal.ZERO;
		List<String> exito = new ArrayList<>();
		for (Servico s : servicos) {
			List<String> av = new ArrayList<>();
			BigDecimal m = s.minimoOab();
			BigDecimal t = s.valorTabela();
			BigDecimal v = s.valorSugeridoTabela();
			if (t == null && v == null && m != null && fatorEscritorio != null) {
				t = m.multiply(fatorEscritorio).setScale(2, RoundingMode.HALF_UP);
				v = t;
				av.add("Sem item na tabela do escritorio: valor calculado como " + fatorEscritorio.stripTrailingZeros().toPlainString() + " x o minimo da OAB");
			}
			if (v == null) {
				v = t;
			}
			if (m == null) {
				av.add("Sem valor minimo da OAB cadastrado para a seccional: nao ha piso para conferir");
			}
			if (v == null && m != null) {
				v = m;
				av.add("Sem valor do escritorio: usado o minimo da OAB");
			}
			if (m != null && v != null && v.compareTo(m) < 0) {
				v = m;
				av.add("Valor sugerido elevado ao minimo da OAB");
			}
			if (v == null) {
				av.add("Sem valor fixo (honorario de exito ou a negociar)");
			}
			if (s.exitoTexto() != null) {
				exito.add(s.tipo() + ": " + s.exitoTexto());
			}
			linhas.add(new Linha(s, m, t == null ? v : t, v, List.copyOf(av)));
			min = min.add(m == null ? BigDecimal.ZERO : m);
			tabela = tabela.add(t != null ? t : v != null ? v : BigDecimal.ZERO);
			sugerido = sugerido.add(v == null ? BigDecimal.ZERO : v);
		}
		BigDecimal pacote = descontoPacote == null ? BigDecimal.ZERO : descontoPacote;
		BigDecimal aplicado = BigDecimal.ZERO;
		if (servicos.size() > 1 && pacote.signum() > 0 && sugerido.signum() > 0) {
			BigDecimal comDesconto = sugerido.multiply(BigDecimal.ONE.subtract(pacote)).setScale(2, RoundingMode.HALF_UP);
			if (comDesconto.compareTo(min) < 0) {
				comDesconto = min;
				avisos.add("Desconto por pacote limitado pelo minimo da OAB");
			}
			aplicado = sugerido.subtract(comDesconto);
			sugerido = comDesconto;
			avisos.add("Desconto por pacote de " + pacote.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "% aplicado (definido pelas socias)");
		}
		return new Quadro(List.copyOf(linhas), min, tabela, sugerido, pacote, aplicado, exito, opcoes(sugerido, tabela, min), avisos);
	}

	/** A vista pelo valor sugerido; parcelado sobre o valor de tabela (sem desconto), sempre respeitando o minimo da OAB. */
	static List<OpcaoParcelamento> opcoes(BigDecimal sugerido, BigDecimal tabela, BigDecimal minimoOab) {
		List<OpcaoParcelamento> r = new ArrayList<>();
		if (sugerido.signum() <= 0) {
			return r;
		}
		r.add(new OpcaoParcelamento("A vista (com o desconto padrao)", sugerido, sugerido, 0, BigDecimal.ZERO, BigDecimal.ZERO));
		BigDecimal base = tabela.max(sugerido).max(minimoOab);
		BigDecimal entrada = base.multiply(ENTRADA_PADRAO).setScale(2, RoundingMode.HALF_UP);
		BigDecimal resto = base.subtract(entrada);
		for (int n : PARCELAS_PADRAO) {
			BigDecimal parcela = resto.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
			BigDecimal ultima = resto.subtract(parcela.multiply(BigDecimal.valueOf(n - 1L)));
			r.add(new OpcaoParcelamento("Entrada de 30% + " + n + " parcelas", base, entrada, n, parcela, ultima));
		}
		// art. 6º da Tabela da OAB: 1/3 na assinatura, 1/3 ate a decisao de 1º grau, 1/3 ao final
		BigDecimal terco = base.divide(BigDecimal.valueOf(3), 2, RoundingMode.DOWN);
		r.add(new OpcaoParcelamento("Em tres etapas (1/3 na assinatura, 1/3 ate a decisao de 1º grau, 1/3 ao final)", base, terco, 2, terco,
				base.subtract(terco.multiply(BigDecimal.valueOf(2)))));
		return r;
	}
}
