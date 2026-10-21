package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import br.com.crispimcampos.gestao.importacao.XlsxLeitor.Aba;
import br.com.crispimcampos.gestao.importacao.XlsxLeitor.Linha;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta a planilha financeira do escritorio: aba "Processos" (um contrato por linha, com plano de pagamento em texto
 * livre), abas "Contas a Receber <mes>" (uma parcela por linha) e "Despesas Mensais". Nada e inventado: o que nao da para
 * entender vira aviso na propria linha (a previa mostra) ou linha ignorada com o motivo.
 */
final class PlanilhaFinanceira {

	enum Situacao { ABERTA, PAGA, PARCIAL, CANCELADA }

	/** Plano de pagamento extraido (melhor esforco) do texto livre "Previsao de Recebimento". */
	record Plano(Integer parcelas, BigDecimal entrada, boolean aoFinalDoProcesso) {
	}

	record Contrato(int linha, String cliente, String clienteNorm, String cnj, String tipoAcao, List<AreaAtuacao> areasSugeridas,
			String situacaoOrigem, BigDecimal valorTotal, BigDecimal percentualExito, BigDecimal valorCausa, String previsao,
			Plano plano, List<String> avisos) {
	}

	record Parcela(String aba, int linha, String cliente, String clienteNorm, LocalDate vencimento, String vencimentoTexto,
			BigDecimal valor, String periodo, String rotulo, int numero, Integer totalParcelas, Situacao situacao,
			String statusOrigem, List<String> avisos) {
	}

	record Despesa(String aba, int linha, LocalDate data, String descricao, String categoria, BigDecimal valor, String forma,
			String referencia) {
	}

	record Ignorado(String aba, int linha, String conteudo, String motivo) {
	}

	record Resultado(List<Contrato> contratos, List<Parcela> parcelas, List<Despesa> despesas, List<Ignorado> ignorados,
			List<String> abasNaoReconhecidas) {
	}

	private static final Pattern PARCELA_N_DE_T = Pattern.compile("PARCELA\\s*(\\d+)\\s*/\\s*(\\d+)");
	private static final Pattern PARCELA_N = Pattern.compile("PARCELA\\s*(\\d+)");
	private static final Pattern VEZES = Pattern.compile("(\\d+)\\s*(?:VEZES|PARCELAS)");
	private static final Pattern REAIS = Pattern.compile("R\\$\\s*([\\d.]+(?:,\\d{2})?)");

	private PlanilhaFinanceira() {
	}

	static Resultado interpretar(List<Aba> abas) {
		List<Contrato> contratos = new ArrayList<>();
		List<Parcela> parcelas = new ArrayList<>();
		List<Despesa> despesas = new ArrayList<>();
		List<Ignorado> ignorados = new ArrayList<>();
		List<String> naoReconhecidas = new ArrayList<>();
		for (Aba aba : abas) {
			String n = Texto.normalizar(aba.nome());
			if (n.equals("PROCESSOS")) {
				processos(aba, contratos, ignorados);
			} else if (n.startsWith("CONTAS A RECEBER")) {
				contasAReceber(aba, parcelas, ignorados);
			} else if (n.startsWith("DESPESAS")) {
				despesas(aba, despesas, ignorados);
			} else {
				naoReconhecidas.add(aba.nome());
			}
		}
		return new Resultado(contratos, parcelas, despesas, ignorados, naoReconhecidas);
	}

	// ---- aba Processos

	private static void processos(Aba aba, List<Contrato> saida, List<Ignorado> ignorados) {
		for (Linha l : aba.linhas()) {
			String cliente = l.texto("A");
			if (Texto.normalizar(cliente).equals("CLIENTE")) {
				continue; // cabecalho
			}
			if (cliente.isBlank()) {
				ignorados.add(new Ignorado(aba.nome(), l.numero(), resumo(l), "Linha sem nome de cliente"));
				continue;
			}
			List<String> avisos = new ArrayList<>();
			String bruto = l.texto("B");
			String cnj = null;
			if (!bruto.isBlank()) {
				String d = NumeroCnj.normalizar(bruto);
				if (NumeroCnj.valido(d)) {
					cnj = d;
				} else {
					avisos.add("Numero de processo fora do padrao CNJ ou com digito verificador invalido: " + bruto);
				}
			}
			String tipo = l.texto("C");
			List<AreaAtuacao> areas = AreaPorTexto.sugerir(tipo);
			if (areas.isEmpty()) {
				avisos.add("Area de atuacao nao identificada em \"" + tipo + "\": escolha na previa");
			} else if (areas.size() > 1) {
				avisos.add("Tipo de acao toca mais de uma area; sugerida " + areas.get(0).rotulo() + ": confira");
			}
			String honorarios = l.texto("E");
			BigDecimal valor = Texto.valor(honorarios);
			if (!honorarios.isBlank() && !Texto.somenteNumero(honorarios)) {
				avisos.add("Honorarios com texto junto do numero (\"" + honorarios + "\"): confira o valor");
			}
			String pct = l.texto("F");
			BigDecimal exito = null;
			if (!pct.isBlank()) {
				BigDecimal p = Texto.valor(pct);
				if (p == null) {
					avisos.add("Percentual de exito nao numerico: \"" + pct + "\"");
				} else {
					exito = p.compareTo(BigDecimal.ONE) <= 0 ? p.multiply(BigDecimal.valueOf(100)).setScale(2) : p;
				}
			}
			BigDecimal causa = Texto.somenteNumero(l.texto("G")) ? Texto.valor(l.texto("G")) : null;
			if (!l.texto("G").isBlank() && causa == null) {
				avisos.add("Valor da causa nao informado em numero: \"" + l.texto("G") + "\"");
			}
			String previsao = l.texto("H");
			saida.add(new Contrato(l.numero(), cliente, Texto.normalizar(cliente), cnj, tipo, areas, l.texto("D"), valor, exito, causa,
					previsao, plano(previsao), avisos));
		}
	}

	static Plano plano(String previsao) {
		String t = Texto.normalizar(previsao);
		Integer n = null;
		Matcher m = VEZES.matcher(t);
		if (m.find()) {
			n = Integer.parseInt(m.group(1));
		}
		BigDecimal entrada = null;
		if (t.contains("ENTRADA") || t.contains("A VISTA")) {
			Matcher r = REAIS.matcher(previsao.toUpperCase());
			if (r.find()) {
				entrada = Texto.valor(r.group(1));
			}
		}
		return new Plano(n, entrada, t.contains("AO FINAL DO PROCESSO"));
	}

	// ---- abas Contas a Receber

	private static void contasAReceber(Aba aba, List<Parcela> saida, List<Ignorado> ignorados) {
		for (Linha l : aba.linhas()) {
			String cliente = l.texto("A");
			String norm = Texto.normalizar(cliente);
			if (norm.equals("CLIENTE")) {
				continue;
			}
			if (cliente.isBlank() && l.celulas().size() == 1 && !l.texto("B").isBlank()) {
				ignorados.add(new Ignorado(aba.nome(), l.numero(), resumo(l), "Total do mes (calculado, nao e lancamento)"));
				continue;
			}
			if (l.celulas().size() == 1 && l.celulas().containsKey("A")) {
				continue; // titulo da aba ("ABRIL DE 2026")
			}
			if (norm.startsWith("LUCRO") || norm.startsWith("TOTAL")) {
				ignorados.add(new Ignorado(aba.nome(), l.numero(), resumo(l), "Linha de total (calculada, nao e lancamento)"));
				continue;
			}
			if (norm.startsWith("EXTRA")) {
				ignorados.add(new Ignorado(aba.nome(), l.numero(), resumo(l),
						"Lancamento extra sem cliente identificado: lance manualmente vinculando o cliente"));
				continue;
			}
			if (cliente.isBlank()) {
				ignorados.add(new Ignorado(aba.nome(), l.numero(), resumo(l), "Linha sem nome de cliente"));
				continue;
			}
			List<String> avisos = new ArrayList<>();
			BigDecimal valor = Texto.valor(l.texto("B"));
			if (valor == null || valor.signum() <= 0) {
				ignorados.add(new Ignorado(aba.nome(), l.numero(), resumo(l), "Valor da parcela ausente ou invalido"));
				continue;
			}
			LocalDate venc = Texto.data(l.texto("C"));
			if (venc == null) {
				avisos.add("Vencimento nao e uma data (\"" + l.texto("C") + "\"): informe a data na previa");
			}
			String periodo = l.texto("D");
			String statusOrigem = l.texto("E");
			Situacao situacao = situacao(statusOrigem, avisos);
			int numero;
			Integer total = null;
			String p = Texto.normalizar(periodo);
			Matcher a = PARCELA_N_DE_T.matcher(p);
			Matcher b = PARCELA_N.matcher(p);
			if (a.find()) {
				numero = Integer.parseInt(a.group(1));
				total = Integer.parseInt(a.group(2));
			} else if (p.startsWith("ENTRADA")) {
				numero = 0;
			} else if (p.equals("VALOR TOTAL")) {
				numero = 1;
				total = 1;
			} else if (b.find()) {
				numero = Integer.parseInt(b.group(1));
			} else {
				numero = -1;
				avisos.add("Periodo nao reconhecido (\"" + periodo + "\"): a parcela sera numerada automaticamente");
			}
			if (p.contains("CANCELAMENTO") && situacao != Situacao.CANCELADA) {
				avisos.add("Periodo indica cancelamento, mas o status e \"" + statusOrigem + "\"");
			}
			String rotulo = p.isBlank() ? "PARCELA SEM ROTULO" : p;
			saida.add(new Parcela(aba.nome(), l.numero(), cliente, norm, venc, l.texto("C"), valor, periodo, rotulo, numero, total,
					situacao, statusOrigem, avisos));
		}
	}

	static Situacao situacao(String status, List<String> avisos) {
		String s = Texto.normalizar(status);
		if (s.isEmpty() || s.equals("EM ABERTO")) {
			return Situacao.ABERTA;
		}
		if (s.equals("PAGO")) {
			return Situacao.PAGA;
		}
		if (s.equals("PAGO METADE")) {
			avisos.add("Pago pela metade: sera registrada metade do valor como paga (confira)");
			return Situacao.PARCIAL;
		}
		if (s.contains("CANCELAMENTO") || s.equals("CANCELADO") || s.equals("CANCELADA")) {
			return Situacao.CANCELADA;
		}
		avisos.add("Status nao reconhecido (\"" + status + "\"): tratado como em aberto");
		return Situacao.ABERTA;
	}

	// ---- aba Despesas Mensais

	private static void despesas(Aba aba, List<Despesa> saida, List<Ignorado> ignorados) {
		String referencia = null;
		for (Linha l : aba.linhas()) {
			String a = Texto.normalizar(l.texto("A"));
			if (a.startsWith("DESPESAS DE")) {
				referencia = l.texto("A");
				continue;
			}
			if (a.equals("DATA")) {
				continue;
			}
			LocalDate data = Texto.data(l.texto("A"));
			BigDecimal valor = Texto.valor(l.texto("D"));
			if (data == null || valor == null || valor.signum() <= 0 || l.texto("B").isBlank()) {
				ignorados.add(new Ignorado(aba.nome(), l.numero(), resumo(l), "Linha de despesa incompleta ou de total"));
				continue;
			}
			saida.add(new Despesa(aba.nome(), l.numero(), data, l.texto("B"), emBranco(l.texto("C")), valor, emBranco(l.texto("E")),
					referencia));
		}
	}

	private static String emBranco(String s) {
		return s == null || s.isBlank() ? null : s;
	}

	private static String resumo(Linha l) {
		String s = String.join(" | ", l.celulas().values());
		return s.length() > 160 ? s.substring(0, 160) : s;
	}

	/** Contrato correspondente ao rotulo "n/N": o unico do cliente ou o que tem N parcelas no plano. */
	static Optional<Contrato> contratoDaParcela(Parcela p, List<Contrato> doCliente) {
		if (doCliente.size() == 1) {
			return Optional.of(doCliente.get(0));
		}
		if (p.totalParcelas() != null) {
			List<Contrato> iguais = doCliente.stream()
					.filter(c -> c.plano().parcelas() != null && c.plano().parcelas().equals(p.totalParcelas())).toList();
			if (iguais.size() == 1) {
				return Optional.of(iguais.get(0));
			}
		}
		return Optional.empty();
	}
}
