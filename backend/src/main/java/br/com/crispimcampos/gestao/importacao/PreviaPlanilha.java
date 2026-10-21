package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Acao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Tipo;
import br.com.crispimcampos.gestao.importacao.PlanilhaFinanceira.Contrato;
import br.com.crispimcampos.gestao.importacao.PlanilhaFinanceira.Parcela;
import br.com.crispimcampos.gestao.importacao.PlanilhaFinanceira.Resultado;
import br.com.crispimcampos.gestao.importacao.PlanilhaFinanceira.Situacao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Monta a previa da planilha: classifica cada registro (criar, conflito, sem mudanca, ignorar) e acumula divergencias. */
final class PreviaPlanilha {

	private final Resultado dados;
	private final Existentes existentes;
	private final Long responsavelPadraoId;
	private final List<ItemPrevia> itens = new ArrayList<>();
	private final Map<String, Integer> ordinais = new HashMap<>();
	private final Map<Contrato, String> chaves = new java.util.IdentityHashMap<>();

	private PreviaPlanilha(Resultado dados, Existentes existentes, Long responsavelPadraoId) {
		this.dados = dados;
		this.existentes = existentes;
		this.responsavelPadraoId = responsavelPadraoId;
	}

	static List<ItemPrevia> montar(Resultado dados, Existentes existentes, Long responsavelPadraoId) {
		PreviaPlanilha p = new PreviaPlanilha(dados, existentes, responsavelPadraoId);
		p.calcularChaves();
		p.clientes();
		p.contratosEProcessos();
		p.parcelas();
		p.despesas();
		p.ignorados();
		return List.copyOf(p.itens);
	}

	/** Chave estavel do contrato: cliente + tipo + CNJ, ou ordinal quando nao ha processo. */
	private void calcularChaves() {
		for (Contrato c : dados.contratos()) {
			String ref = c.cnj() != null ? c.cnj() : "#" + proximo("CT|" + c.clienteNorm() + "|" + Texto.normalizar(c.tipoAcao()));
			chaves.put(c, "PLANILHA|" + c.clienteNorm() + "|" + Texto.normalizar(c.tipoAcao()) + "|" + ref);
		}
	}

	// ---- clientes

	private void clientes() {
		Map<String, String> nomes = new LinkedHashMap<>();
		Map<String, Set<AreaAtuacao>> areas = new HashMap<>();
		for (Contrato c : dados.contratos()) {
			nomes.putIfAbsent(c.clienteNorm(), c.cliente());
			Set<AreaAtuacao> s = areas.computeIfAbsent(c.clienteNorm(), k -> new LinkedHashSet<>());
			if (!c.areasSugeridas().isEmpty()) {
				s.add(c.areasSugeridas().get(0));
			}
		}
		for (Parcela p : dados.parcelas()) {
			nomes.putIfAbsent(p.clienteNorm(), p.cliente());
			areas.computeIfAbsent(p.clienteNorm(), k -> new LinkedHashSet<>());
		}
		nomes.forEach((norm, nome) -> {
			List<String> div = new ArrayList<>();
			List<String> sugeridas = areas.get(norm).stream().map(Enum::name).toList();
			Map<String, Object> imp = mapa("nome", nome, "nomeNormalizado", norm, "documento", null, "areas", sugeridas);
			Optional<Map<String, Object>> ex = existentes.cliente(null, norm);
			if (ex.isEmpty()) {
				div.add("Planilha nao traz CPF/CNPJ: cliente novo identificado so pelo nome, conferir manualmente");
				if (sugeridas.isEmpty()) {
					div.add("Area de atuacao pendente: escolha na previa antes de confirmar");
				}
				adicionar(Tipo.CLIENTE, "CLIENTE|" + norm, nome, Acao.CRIAR, div, imp, null);
			} else {
				div.add("Sem CPF/CNPJ na planilha: vinculado ao cliente existente pelo nome, conferir se e a mesma pessoa");
				@SuppressWarnings("unchecked")
				List<String> atuais = (List<String>) ex.get().getOrDefault("areas", List.of());
				boolean traz = !sugeridas.isEmpty() && !atuais.containsAll(sugeridas);
				adicionar(Tipo.CLIENTE, "CLIENTE|" + norm, nome, traz ? Acao.CONFLITO : Acao.SEM_MUDANCA, div, imp, ex.get());
			}
		});
	}

	// ---- contratos e processos

	private void contratosEProcessos() {
		Map<String, List<Parcela>> parcelasPorCliente = new HashMap<>();
		dados.parcelas().forEach(p -> parcelasPorCliente.computeIfAbsent(p.clienteNorm(), k -> new ArrayList<>()).add(p));
		for (Contrato c : dados.contratos()) {
			List<String> div = new ArrayList<>(c.avisos());
			String chave = chaves.get(c);
			AreaAtuacao area = c.areasSugeridas().isEmpty() ? null : c.areasSugeridas().get(0);
			BigDecimal valor = c.valorTotal();
			List<Parcela> doCliente = parcelasPorCliente.getOrDefault(c.clienteNorm(), List.of());
			BigDecimal soma = doCliente.stream().filter(p -> p.situacao() != Situacao.CANCELADA && contratoDe(p).map(x -> x == c).orElse(false))
					.map(Parcela::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
			if (valor == null) {
				if (c.percentualExito() != null || c.plano().aoFinalDoProcesso()) {
					valor = BigDecimal.ZERO;
					div.add("Sem valor fixo de honorarios (so exito/ao final do processo)");
				} else if (soma.signum() > 0) {
					valor = soma;
					div.add("Valor total nao informado: usada a soma das parcelas da planilha (" + soma + ")");
				} else {
					valor = BigDecimal.ZERO;
					div.add("Valor total dos honorarios nao informado");
				}
			} else if (soma.compareTo(valor) > 0 && c.plano().parcelas() != null) {
				div.add("As parcelas lancadas na planilha somam " + soma + ", mais que o valor contratado " + valor);
			}
			if (c.cnj() == null) {
				div.add("Contrato sem numero de processo");
			}
			Map<String, Object> imp = mapa("clienteNorm", c.clienteNorm(), "clienteNome", c.cliente(), "cnj", c.cnj(), "tipoAcao", c.tipoAcao(),
					"area", area == null ? null : area.name(), "situacaoOrigem", c.situacaoOrigem(), "valorTotal", valor,
					"percentualExito", c.percentualExito(), "valorCausa", c.valorCausa(), "formaPagamento", c.previsao(),
					"numeroParcelas", c.plano().parcelas(), "aConferir", false, "descricao", descricao(c));
			if (area == null) {
				div.add("Area de atuacao pendente: escolha na previa antes de confirmar");
			}
			Optional<Map<String, Object>> ex = existentes.contrato(chave);
			adicionar(Tipo.CONTRATO, chave, c.cliente() + " — " + c.tipoAcao(), classificar(ex, imp, "valorTotal", "percentualExito",
					"formaPagamento", "situacaoOrigem", "area"), div, imp, ex.orElse(null));
			if (c.cnj() != null) {
				processo(c, area);
			}
		}
	}

	private void processo(Contrato c, AreaAtuacao area) {
		List<String> div = new ArrayList<>();
		String tribunal = TribunalCnj.de(c.cnj());
		if (tribunal == null) {
			div.add("Tribunal nao identificado pelo numero: informe na previa");
		} else {
			div.add("Tribunal inferido do numero CNJ: " + tribunal);
		}
		Map<String, Object> imp = mapa("cnj", c.cnj(), "clienteNorm", c.clienteNorm(), "clienteNome", c.cliente(), "tribunal", tribunal,
				"classe", c.tipoAcao(), "situacao", c.situacaoOrigem(), "valorCausa", c.valorCausa(), "area", area == null ? null : area.name(),
				"advogadoId", responsavelPadraoId);
		Optional<Map<String, Object>> ex = existentes.processo(c.cnj());
		Acao acao = Acao.CRIAR;
		if (ex.isPresent()) {
			Object doutro = ex.get().get("clienteNorm");
			if (doutro != null && !doutro.equals(c.clienteNorm())) {
				acao = Acao.CONFLITO;
				div.add("Processo ja cadastrado para outro cliente (" + doutro + "): conferir o vinculo");
			} else {
				acao = Acao.SEM_MUDANCA;
			}
		}
		adicionar(Tipo.PROCESSO, "CNJ|" + c.cnj(), c.cnj(), acao, div, imp, ex.orElse(null));
	}

	private Optional<Contrato> contratoDe(Parcela p) {
		List<Contrato> doCliente = dados.contratos().stream().filter(c -> c.clienteNorm().equals(p.clienteNorm())).toList();
		return PlanilhaFinanceira.contratoDaParcela(p, doCliente);
	}

	private static String descricao(Contrato c) {
		return "Honorarios — " + (c.tipoAcao().isBlank() ? "contrato importado da planilha" : c.tipoAcao());
	}

	// ---- parcelas

	private void parcelas() {
		Map<String, Set<Integer>> numerosUsados = new HashMap<>();
		Set<String> avulsosCriados = new LinkedHashSet<>();
		Map<String, BigDecimal> somaAvulso = new HashMap<>();
		List<Object[]> pendentes = new ArrayList<>();
		for (Parcela p : dados.parcelas()) {
			List<String> div = new ArrayList<>(p.avisos());
			String contratoChave;
			Optional<Contrato> c = contratoDe(p);
			if (c.isPresent()) {
				contratoChave = chaveDoContrato(c.get());
			} else {
				long qtd = dados.contratos().stream().filter(x -> x.clienteNorm().equals(p.clienteNorm())).count();
				contratoChave = "PLANILHA|" + p.clienteNorm() + "|AVULSO";
				div.add(qtd == 0 ? "Parcela sem contrato correspondente na planilha: sera vinculada a um contrato avulso (a conferir)"
						: "Parcela pode pertencer a qualquer um dos " + qtd + " contratos do cliente: vinculada a contrato avulso (a conferir)");
				avulsosCriados.add(contratoChave);
				somaAvulso.merge(contratoChave, p.situacao() == Situacao.CANCELADA ? BigDecimal.ZERO : p.valor(), BigDecimal::add);
			}
			int numero = p.numero();
			Set<Integer> usados = numerosUsados.computeIfAbsent(contratoChave, k -> new LinkedHashSet<>());
			if (numero < 0 || usados.contains(numero)) {
				int novo = usados.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
				div.add(numero < 0 ? "Parcela numerada automaticamente (" + novo + ")"
						: "Numero de parcela repetido no mesmo contrato (" + numero + "): renumerada para " + novo);
				numero = novo;
			}
			usados.add(numero);
			String chave = "PLANILHA|" + p.clienteNorm() + "|" + p.rotulo() + "|" + proximo("PC|" + p.clienteNorm() + "|" + p.rotulo());
			BigDecimal pago = switch (p.situacao()) {
				case PAGA -> p.valor();
				case PARCIAL -> p.valor().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
				default -> null;
			};
			Map<String, Object> imp = mapa("contratoChave", contratoChave, "clienteNorm", p.clienteNorm(), "clienteNome", p.cliente(), "numero", numero,
					"rotulo", p.rotulo(), "vencimento", p.vencimento() == null ? null : p.vencimento().toString(), "valor", p.valor(),
					"situacao", p.situacao().name(), "valorPago", pago, "statusOrigem", p.statusOrigem(), "aba", p.aba());
			Optional<Map<String, Object>> ex = existentes.parcela(chave);
			pendentes.add(new Object[] {chave, p, imp, div, ex});
		}
		for (String chave : avulsosCriados) {
			String norm = chave.split("\\|")[1];
			String nome = dados.parcelas().stream().filter(x -> x.clienteNorm().equals(norm)).map(Parcela::cliente).findFirst().orElse(norm);
			Map<String, Object> imp = mapa("clienteNorm", norm, "clienteNome", nome, "cnj", null, "tipoAcao", "Lancamentos da planilha", "area", null,
					"valorTotal", somaAvulso.getOrDefault(chave, BigDecimal.ZERO), "descricao", "Lancamentos da planilha sem contrato correspondente",
					"aConferir", true);
			List<String> div = new ArrayList<>(List.of("Contrato avulso criado para abrigar parcelas sem contrato correspondente: conferir e unificar"));
			Optional<Map<String, Object>> ex = existentes.contrato(chave);
			adicionar(Tipo.CONTRATO, chave, nome + " — lancamentos sem contrato", classificar(ex, imp, "valorTotal"), div, imp, ex.orElse(null));
		}
		for (Object[] o : pendentes) {
			@SuppressWarnings("unchecked")
			List<String> div = (List<String>) o[3];
			@SuppressWarnings("unchecked")
			Map<String, Object> imp = (Map<String, Object>) o[2];
			@SuppressWarnings("unchecked")
			Optional<Map<String, Object>> ex = (Optional<Map<String, Object>>) o[4];
			Parcela p = (Parcela) o[1];
			adicionar(Tipo.PARCELA, (String) o[0], p.cliente() + " — " + p.rotulo() + " (" + p.aba().replace("Contas a Receber ", "") + ")",
					classificar(ex, imp, "valor", "vencimento", "situacao"), div, imp, ex.orElse(null));
		}
	}

	private String chaveDoContrato(Contrato c) {
		return chaves.get(c);
	}

	// ---- despesas e ignorados

	private void despesas() {
		for (var d : dados.despesas()) {
			String chave = "PLANILHA|DESP|" + d.data() + "|" + Texto.normalizar(d.descricao()) + "|" + d.valor() + "|"
					+ proximo("DP|" + d.data() + "|" + Texto.normalizar(d.descricao()) + "|" + d.valor());
			Map<String, Object> imp = mapa("data", d.data().toString(), "descricao", d.descricao(), "categoria", d.categoria(), "valor", d.valor(),
					"forma", d.forma(), "referencia", d.referencia());
			Optional<Map<String, Object>> ex = existentes.despesaEscritorio(chave);
			List<String> div = new ArrayList<>();
			div.add("Despesa do escritorio (sem cliente/processo vinculado na planilha)");
			adicionar(Tipo.DESPESA, chave, d.descricao() + " — " + d.valor(), ex.isPresent() ? Acao.SEM_MUDANCA : Acao.CRIAR, div, imp, ex.orElse(null));
		}
	}

	private void ignorados() {
		for (var i : dados.ignorados()) {
			Tipo tipo = Texto.normalizar(i.aba()).equals("PROCESSOS") ? Tipo.CONTRATO
					: Texto.normalizar(i.aba()).startsWith("DESPESAS") ? Tipo.DESPESA : Tipo.PARCELA;
			adicionar(tipo, "IGNORADO|" + i.aba() + "|" + i.linha(), i.aba() + " linha " + i.linha(), Acao.IGNORAR, List.of(i.motivo()),
					mapa("conteudo", i.conteudo()), null);
		}
	}

	// ---- util

	private int proximo(String base) {
		return ordinais.merge(base, 1, Integer::sum);
	}

	private void adicionar(Tipo tipo, String chave, String rotulo, Acao acao, List<String> div, Map<String, Object> imp, Map<String, Object> ex) {
		itens.add(new ItemPrevia(null, itens.size(), tipo, chave, rotulo, acao, List.copyOf(div), imp, ex, null, Map.of(), null, null));
	}

	/** Sem registro existente: CRIAR. Existente igual nos campos comparados (ignorando o que a fonte nao informa): SEM_MUDANCA. Senao CONFLITO. */
	static Acao classificar(Optional<Map<String, Object>> existente, Map<String, Object> importado, String... campos) {
		if (existente.isEmpty()) {
			return Acao.CRIAR;
		}
		for (String c : campos) {
			if (importado.get(c) == null) {
				continue; // a fonte nao informa: nao ha o que comparar (o dado do sistema permanece)
			}
			if (!iguais(importado.get(c), existente.get().get(c))) {
				return Acao.CONFLITO;
			}
		}
		return Acao.SEM_MUDANCA;
	}

	static boolean iguais(Object a, Object b) {
		if (a == null || b == null) {
			return a == b;
		}
		if (a instanceof Number x && b instanceof Number y) {
			return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
		}
		return String.valueOf(a).trim().equals(String.valueOf(b).trim());
	}

	static Map<String, Object> mapa(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}
}
