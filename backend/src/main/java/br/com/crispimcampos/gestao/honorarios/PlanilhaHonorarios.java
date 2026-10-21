package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.importacao.XlsxLeitor;
import br.com.crispimcampos.gestao.importacao.XlsxLeitor.Aba;
import br.com.crispimcampos.gestao.importacao.XlsxLeitor.Linha;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le a planilha de honorarios (aba "Honorarios por Area" + aba "Parametros"): uma linha por servico, com o minimo da OAB e a
 * politica do escritorio. Os valores em R$ vem prontos (formulas ja calculadas); linhas "sem valor fixo" (so exito) ficam sem valor.
 */
public final class PlanilhaHonorarios {

	public record Servico(int linha, String areaOriginal, AreaAtuacao area, String tipo, String fase, String itemOab, BigDecimal urh, BigDecimal minimoOab,
			BigDecimal valorTabela, BigDecimal desconto, BigDecimal valorSugerido, String exitoTexto, BigDecimal exitoMin, BigDecimal exitoMax,
			String parcelamento, String observacoes, boolean derivado, List<String> avisos) {
	}

	public record Parametros(String referencia, BigDecimal valorUrh, BigDecimal fator, BigDecimal descontoPadrao, BigDecimal descontoTrabalhista) {
	}

	public record Resultado(Parametros parametros, List<Servico> servicos, List<String> avisos) {
	}

	private static final Pattern PERCENTUAL = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*%");

	private PlanilhaHonorarios() {
	}

	public static Resultado ler(byte[] arquivo) {
		List<Aba> abas = XlsxLeitor.ler(arquivo);
		Aba tabela = abas.stream().filter(a -> ChaveServico.normalizar(a.nome()).startsWith("HONORARIOS")).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Aba \"Honorarios por Area\" nao encontrada na planilha"));
		Parametros parametros = abas.stream().filter(a -> ChaveServico.normalizar(a.nome()).startsWith("PARAMETROS")).findFirst().map(PlanilhaHonorarios::parametros)
				.orElse(new Parametros(null, null, null, null, null));
		List<String> avisos = new ArrayList<>();
		List<Servico> servicos = new ArrayList<>();
		boolean cabecalho = false;
		for (Linha l : tabela.linhas()) {
			if (!cabecalho) {
				cabecalho = ChaveServico.normalizar(l.texto("A")).startsWith("AREA DE ATUACAO");
				continue;
			}
			String areaOriginal = l.texto("A");
			String tipo = l.texto("B");
			if (areaOriginal.isBlank() || tipo.isBlank()) {
				continue;
			}
			servicos.add(servico(l, areaOriginal, tipo, parametros));
		}
		if (servicos.isEmpty()) {
			throw new IllegalArgumentException("Nenhum servico encontrado na planilha de honorarios");
		}
		if (parametros.valorUrh() == null) {
			avisos.add("Valor da URH nao encontrado na aba de parametros");
		}
		return new Resultado(parametros, servicos, avisos);
	}

	private static Servico servico(Linha l, String areaOriginal, String tipo, Parametros p) {
		List<String> avisos = new ArrayList<>();
		AreaAtuacao area = area(areaOriginal, tipo);
		if (area == null) {
			avisos.add("Area \"" + areaOriginal + "\" sem equivalente entre as areas do sistema: defina a area do servico");
		}
		String exito = traco(l.texto("C"));
		BigDecimal min = null;
		BigDecimal max = null;
		if (exito != null) {
			Matcher m = PERCENTUAL.matcher(exito);
			while (m.find()) {
				BigDecimal v = new BigDecimal(m.group(1).replace(',', '.'));
				min = min == null ? v : min.min(v);
				max = max == null ? v : max.max(v);
			}
		}
		BigDecimal urh = numero(l.texto("D"));
		BigDecimal minimo = numero(l.texto("E"));
		BigDecimal tabela = numero(l.texto("H"));
		BigDecimal desconto = numero(l.texto("I"));
		BigDecimal sugerido = numero(l.texto("J"));
		boolean derivado = minimo != null && tabela != null && p.fator() != null
				&& minimo.multiply(p.fator()).setScale(2, RoundingMode.HALF_UP).subtract(tabela).abs().compareTo(new BigDecimal("0.02")) <= 0;
		if (minimo == null && urh == null) {
			avisos.add("Sem valor minimo fixo (honorario de exito ou a negociar)");
		}
		return new Servico(l.numero(), areaOriginal, area, tipo, fase(tipo, l.texto("G")), traco(l.texto("F")), urh, minimo, tabela, desconto,
				sugerido == null ? null : sugerido.setScale(2, RoundingMode.HALF_UP), exito, min, max, traco(l.texto("K")), traco(l.texto("G")), derivado, avisos);
	}

	private static Parametros parametros(Aba aba) {
		String referencia = null;
		BigDecimal urh = null;
		BigDecimal fator = null;
		BigDecimal descPadrao = null;
		BigDecimal descTrab = null;
		for (Linha l : aba.linhas()) {
			String rotulo = ChaveServico.normalizar(l.texto("B"));
			BigDecimal v = numero(l.texto("C"));
			if (rotulo.startsWith("VALOR DA URH")) {
				urh = v;
			} else if (rotulo.startsWith("MES ANO DE REFERENCIA")) {
				referencia = traco(l.texto("C"));
			} else if (rotulo.startsWith("FATOR DE TABELA")) {
				fator = v;
			} else if (rotulo.startsWith("DESCONTO PADRAO MAXIMO")) {
				descPadrao = v;
			} else if (rotulo.startsWith("DESCONTO PADRAO") && rotulo.contains("TRABALHISTA")) {
				descTrab = v;
			}
		}
		return new Parametros(referencia, urh, fator, descPadrao, descTrab);
	}

	/** Area do sistema a partir da area da planilha e do tipo de servico; null quando nao ha equivalente claro. */
	static AreaAtuacao area(String areaOriginal, String tipo) {
		String a = ChaveServico.normalizar(areaOriginal);
		String t = ChaveServico.normalizar(tipo);
		if (a.contains("FAMILIA")) {
			return t.contains("INVENTARIO") || t.contains("TESTAMENT") || t.contains("HERANCA") || t.contains("ARROLAMENTO") || t.contains("SUCESS")
					|| t.contains("CODICILO") ? AreaAtuacao.SUCESSAO : AreaAtuacao.FAMILIA;
		}
		if (a.contains("TRABALH")) {
			return AreaAtuacao.TRABALHISTA;
		}
		if (a.contains("PREVID")) {
			return AreaAtuacao.PREVIDENCIARIO;
		}
		if (a.contains("IMOBILI")) {
			return AreaAtuacao.IMOBILIARIO;
		}
		if (a.contains("CONDOMIN")) {
			return AreaAtuacao.CONDOMINIAL;
		}
		if (a.contains("PENAL") || a.contains("CRIMINAL")) {
			return AreaAtuacao.CRIMINAL;
		}
		if (a.contains("MILITAR") || a.contains("ADMINISTRAT")) {
			return t.contains("MILITAR") ? AreaAtuacao.MILITAR : AreaAtuacao.ADMINISTRATIVO;
		}
		if (a.contains("CIVEL") || a.contains("CONTRATO")) {
			return AreaAtuacao.CIVEL;
		}
		return null;
	}

	/** Fase/rito inferida do texto do servico; null quando nao ha (o servico e unico). */
	static String fase(String tipo, String observacao) {
		String t = ChaveServico.normalizar(tipo + " " + observacao);
		if (t.contains("RECURSO") || t.contains("RAZOES") || t.contains("CONTRARRAZOES") || t.contains("APELACAO") || t.contains("AGRAVO")) {
			return "RECURSO";
		}
		if (t.contains("EXECUCAO") || t.contains("CUMPRIMENTO")) {
			return "CUMPRIMENTO";
		}
		if (t.contains("EXTRAJUDICIAL") || t.contains("PARECER") || t.contains("CONSULTA") || t.contains("MINUTA")) {
			return "EXTRAJUDICIAL";
		}
		if (t.contains("JUDICIAL") && !t.contains("ADMINISTRATIV")) { // "judicial ou administrativo" nao define uma fase
			return "JUDICIAL";
		}
		return null;
	}

	private static String traco(String s) {
		return s == null || s.isBlank() || s.trim().equals("—") || s.trim().equals("-") ? null : s.trim();
	}

	private static BigDecimal numero(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
