package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.documentos.DocumentoClienteService;
import br.com.crispimcampos.gestao.documentos.TipoDocumento;
import br.com.crispimcampos.gestao.financeiro.FinanceiroImportacao;
import br.com.crispimcampos.gestao.financeiro.FinanceiroImportacao.DadosContrato;
import br.com.crispimcampos.gestao.financeiro.FinanceiroImportacao.DadosParcela;
import br.com.crispimcampos.gestao.importacao.ImportacaoRepositorio.Cabecalho;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Acao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Resultado;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Tipo;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Grava os itens aprovados da previa na ordem de dependencia (clientes, processos, contratos, parcelas, despesas), cada item em
 * sua propria transacao: um erro vira "ERRO" no relatorio e nao derruba o resto. Nunca apaga nem reduz dados existentes.
 */
@Component
class AplicadorImportacao {

	private static final List<Tipo> ORDEM = List.of(Tipo.CLIENTE, Tipo.PROCESSO, Tipo.CONTRATO, Tipo.PARCELA, Tipo.DESPESA, Tipo.DOCUMENTO);

	private final CadastroService cadastros;
	private final FinanceiroImportacao financeiro;
	private final ImportacaoRepositorio repo;
	private final DocumentoClienteService documentos;
	private final DriveFontes fontes;
	private final TransactionTemplate tx;

	AplicadorImportacao(CadastroService cadastros, FinanceiroImportacao financeiro, ImportacaoRepositorio repo, DocumentoClienteService documentos,
			DriveFontes fontes, PlatformTransactionManager tm) {
		this.documentos = documentos;
		this.fontes = fontes;
		this.cadastros = cadastros;
		this.financeiro = financeiro;
		this.repo = repo;
		this.tx = new TransactionTemplate(tm);
		this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Ids resolvidos durante a aplicacao (chave natural -> id gravado ou ja existente). */
	private record Ids(Map<String, Long> clientes, Map<String, Long> processos, Map<String, Long> contratos) {
	}

	Map<String, Object> aplicar(UsuarioLogado ator, Cabecalho imp, List<ItemPrevia> itens) {
		String origem = imp.origem();
		Ids ids = new Ids(new HashMap<>(), new HashMap<>(), new HashMap<>());
		Map<String, Set<String>> areasDosClientes = areasEfetivasDosClientes(itens);
		Map<Tipo, Map<String, Integer>> totais = new LinkedHashMap<>();
		List<Map<String, Object>> ignorados = new ArrayList<>();
		List<Map<String, Object>> erros = new ArrayList<>();
		for (Tipo tipo : ORDEM) {
			for (ItemPrevia i : itens) {
				if (i.tipo() != tipo) {
					continue;
				}
				Resultado r;
				String motivo = null;
				if (i.acao() == Acao.IGNORAR) {
					r = Resultado.IGNORADO;
					motivo = i.divergencias().isEmpty() ? "Nao importavel" : i.divergencias().get(0);
				} else if (i.acao() == Acao.SEM_MUDANCA) {
					registrarExistente(i, ids);
					r = Resultado.IGNORADO;
					motivo = "Sem mudanca: ja existe igual no sistema";
				} else if (!i.aplicar()) {
					registrarExistente(i, ids);
					r = Resultado.IGNORADO;
					motivo = i.acao() == Acao.CONFLITO ? "Conflito: mantido o dado existente" : "Nao importado por decisao da socia";
				} else {
					try {
						boolean atualizou = i.acao() == Acao.CONFLITO;
						tx.executeWithoutResult(s -> gravar(ator, origem, imp, i, ids, areasDosClientes, itens));
						r = Resultado.APLICADO;
						boolean sobrescreveu = "true".equals(String.valueOf(i.ajustes().get("sobrescrever")));
						motivo = !atualizou ? "Criado" : "DRIVE".equals(origem) && !sobrescreveu
								? "Conflito: complementado sem sobrescrever; dados existentes mantidos" : "Atualizado com a versao importada";
					} catch (RuntimeException e) {
						r = Resultado.ERRO;
						motivo = mensagem(e);
						erros.add(Map.of("item", i.rotulo(), "tipo", i.tipo().name(), "motivo", motivo));
					}
				}
				if (r == Resultado.IGNORADO) {
					ignorados.add(Map.of("item", i.rotulo(), "tipo", i.tipo().name(), "motivo", motivo));
				}
				repo.marcarResultado(i.id(), r, motivo);
				String rotuloContagem = r == Resultado.APLICADO ? (i.acao() == Acao.CONFLITO ? "atualizados" : "criados")
						: r == Resultado.ERRO ? "erros" : i.acao() == Acao.IGNORAR ? "ignorados" : "mantidos";
				totais.computeIfAbsent(tipo, k -> new LinkedHashMap<>()).merge(rotuloContagem, 1, Integer::sum);
			}
		}
		Map<String, Object> relatorio = new LinkedHashMap<>();
		Map<String, Object> tot = new LinkedHashMap<>();
		totais.forEach((k, v) -> tot.put(k.name(), v));
		relatorio.put("totais", tot);
		relatorio.put("ignorados", ignorados);
		relatorio.put("erros", erros);
		return relatorio;
	}

	private void gravar(UsuarioLogado ator, String origem, Cabecalho imp, ItemPrevia i, Ids ids, Map<String, Set<String>> areasDosClientes,
			List<ItemPrevia> todos) {
		switch (i.tipo()) {
			case CLIENTE -> gravarCliente(ator, origem, i, ids, areasDosClientes);
			case PROCESSO -> gravarProcesso(ator, imp, i, ids, areasDosClientes, todos);
			case CONTRATO -> gravarContrato(origem, i, ids, areasDosClientes);
			case PARCELA -> gravarParcela(origem, i, ids);
			case DOCUMENTO -> gravarDocumento(ator, imp, i, ids);
			case DESPESA -> {
				var m = i.importado();
				financeiro.criarDespesaEscritorio(LocalDate.parse(String.valueOf(m.get("data"))), texto(m.get("descricao")), texto(m.get("categoria")),
						dec(m.get("valor")), texto(m.get("forma")), origem, i.chave());
			}
			default -> throw new IllegalStateException("Tipo sem aplicacao: " + i.tipo());
		}
	}

	private void gravarCliente(UsuarioLogado ator, String origem, ItemPrevia i, Ids ids, Map<String, Set<String>> areasDosClientes) {
		String norm = String.valueOf(i.importado().get("nomeNormalizado"));
		Set<AreaAtuacao> areas = paraAreas(areasDosClientes.getOrDefault(norm, Set.of()));
		var m = i.importado();
		if (i.acao() == Acao.CRIAR) {
			var c = cadastros.criarClienteImportado(ator.id(), texto(m.get("nome")), texto(m.get("documento")), texto(m.get("email")), texto(m.get("telefone")),
					texto(m.get("endereco")), "Importado da " + (origem.equals("DRIVE") ? "pasta do Drive" : "planilha") + " (conferir cadastro)", areas, true, origem);
			if (texto(m.get("rg")) != null || texto(m.get("estadoCivil")) != null) {
				cadastros.completarDadosCadastrais(ator.id(), c.id(), texto(m.get("rg")), texto(m.get("estadoCivil")), null, null, null);
			}
			ids.clientes().put(norm, c.id());
		} else {
			Long id = numero(i.existente().get("id"));
			if (!areas.isEmpty()) {
				cadastros.adicionarAreas(ator.id(), id, areas);
			}
			cadastros.aplicarDadosImportados(ator.id(), id, new CadastroService.DadosImportados(texto(m.get("documento")), texto(m.get("rg")), texto(m.get("estadoCivil")),
					texto(m.get("endereco")), texto(m.get("telefone")), texto(m.get("email"))), Boolean.TRUE.equals(i.ajustes().get("sobrescrever")) || "true".equals(String.valueOf(i.ajustes().get("sobrescrever"))));
			ids.clientes().put(norm, id);
		}
	}

	private void gravarProcesso(UsuarioLogado ator, Cabecalho imp, ItemPrevia i, Ids ids, Map<String, Set<String>> areasDosClientes, List<ItemPrevia> todos) {
		var m = i.importado();
		String cnj = texto(m.get("cnj"));
		if (i.acao() == Acao.CONFLITO) {
			ids.processos().put(cnj, numero(i.existente().get("id")));
			return;
		}
		Long clienteId = exigir(ids.clientes().get(String.valueOf(m.get("clienteNorm"))), "Cliente do processo nao disponivel");
		AreaAtuacao area = areaDoProcessoOuNull(i, todos, areasDosClientes);
		if (area == null) {
			throw new IllegalStateException("Area de atuacao do processo nao definida");
		}
		String tribunal = texto(i.valor("tribunal"));
		Long advogado = numero(m.get("advogadoId")) != null ? numero(m.get("advogadoId")) : imp.responsavelPadraoId();
		var p = cadastros.criarProcesso(ator.id(), cnj, tribunal == null ? "N/I" : tribunal, null, texto(m.get("classe")), texto(m.get("situacao")),
				dec(m.get("valorCausa")), clienteId, advogado, area);
		ids.processos().put(cnj, p.id());
	}

	private void gravarContrato(String origem, ItemPrevia i, Ids ids, Map<String, Set<String>> areasDosClientes) {
		var m = i.importado();
		if (i.acao() == Acao.CONFLITO && "DRIVE".equals(origem)) {
			Long id = numero(i.existente().get("id"));
			financeiro.complementarContrato(id, dec(i.valor("valorTotal")), data(m.get("dataAssinatura")), texto(m.get("formaPagamento")),
					m.get("numeroParcelas") == null ? null : numero(m.get("numeroParcelas")).intValue(),
					"true".equals(String.valueOf(i.ajustes().get("sobrescrever"))));
			ids.contratos().put(i.chave(), id);
			return;
		}
		if (i.acao() == Acao.CONFLITO) {
			Long id = numero(i.existente().get("id"));
			financeiro.atualizarContrato(id, dec(i.valor("valorTotal")), area(areaDoContrato(i, areasDosClientes)), texto(m.get("formaPagamento")),
					dec(m.get("percentualExito")), texto(m.get("situacaoOrigem")));
			ids.contratos().put(i.chave(), id);
			return;
		}
		Long clienteId = exigir(ids.clientes().get(String.valueOf(m.get("clienteNorm"))), "Cliente do contrato nao disponivel");
		String cnj = texto(m.get("cnj"));
		Long processoId = cnj == null ? null : ids.processos().get(cnj);
		BigDecimal valorTotal = dec(i.valor("valorTotal"));
		Long id = financeiro.criarContrato(new DadosContrato(clienteId, processoId, texto(m.get("descricao")), valorTotal == null ? BigDecimal.ZERO : valorTotal,
				area(areaDoContrato(i, areasDosClientes)), origem, i.chave(), texto(m.get("formaPagamento")), dec(m.get("percentualExito")),
				texto(m.get("situacaoOrigem")), m.get("numeroParcelas") == null ? null : numero(m.get("numeroParcelas")).intValue(),
				Boolean.TRUE.equals(m.get("aConferir")), data(m.get("dataAssinatura"))));
		ids.contratos().put(i.chave(), id);
		BigDecimal pct = dec(m.get("percentualExito"));
		BigDecimal causa = dec(m.get("valorCausa"));
		if (pct != null && causa != null && pct.signum() > 0 && causa.signum() > 0) {
			financeiro.criarHonorarioDeExito(clienteId, processoId, "Honorarios de exito (" + pct.stripTrailingZeros().toPlainString() + "% do valor da causa)",
					causa.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
		}
	}

	private void gravarParcela(String origem, ItemPrevia i, Ids ids) {
		var m = i.importado();
		if (i.acao() == Acao.CONFLITO) {
			BigDecimal valor = dec(i.valor("valor"));
			String sit = texto(m.get("situacao"));
			financeiro.atualizarParcela(numero(i.existente().get("id")), LocalDate.parse(String.valueOf(i.valor("vencimento"))), valor, sit,
					valorPago(sit, valor, dec(m.get("valorPago"))));
			return;
		}
		Long contratoId = exigir(ids.contratos().get(String.valueOf(m.get("contratoChave"))), "Contrato da parcela nao disponivel");
		BigDecimal valor = dec(i.valor("valor"));
		String sit = texto(m.get("situacao"));
		financeiro.criarParcela(new DadosParcela(contratoId, numero(m.get("numero")).intValue(), LocalDate.parse(String.valueOf(i.valor("vencimento"))), valor, sit,
				valorPago(sit, valor, dec(m.get("valorPago"))), null, null, texto(m.get("rotulo")), origem, i.chave()));
	}

	private static BigDecimal valorPago(String situacao, BigDecimal valor, BigDecimal importado) {
		return switch (situacao) {
			case "PAGA" -> valor;
			case "PARCIAL" -> importado != null ? importado : valor.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
			default -> null;
		};
	}

	/** Itens mantidos/sem mudanca ainda alimentam o mapa de ids, para os dependentes (ex.: parcela de contrato ja existente). */
	private static void registrarExistente(ItemPrevia i, Ids ids) {
		if (i.existente() == null || i.existente().get("id") == null) {
			return;
		}
		Long id = numero(i.existente().get("id"));
		switch (i.tipo()) {
			case CLIENTE -> ids.clientes().put(String.valueOf(i.importado().get("nomeNormalizado")), id);
			case PROCESSO -> ids.processos().put(texto(i.importado().get("cnj")), id);
			case CONTRATO -> ids.contratos().put(i.chave(), id);
			default -> {
			}
		}
	}

	// ---- areas efetivas (ajuste da socia > importada > derivada)

	static Map<String, Set<String>> areasEfetivasDosClientes(List<ItemPrevia> itens) {
		Map<String, Set<String>> r = new HashMap<>();
		for (ItemPrevia i : itens) {
			if (i.tipo() == Tipo.CLIENTE && i.acao() != Acao.IGNORAR) {
				List<String> a = lista(i.ajustes().containsKey("areas") ? i.ajustes().get("areas") : i.importado().get("areas"));
				Set<String> destino = r.computeIfAbsent(String.valueOf(i.importado().get("nomeNormalizado")), k -> new LinkedHashSet<>());
				destino.addAll(a);
				if (i.existente() != null) { // areas que o cliente ja tem no sistema valem para contratos e processos dele
					destino.addAll(lista(i.existente().get("areas")));
				}
			}
		}
		for (ItemPrevia i : itens) {
			if (i.tipo() == Tipo.CONTRATO && i.acao() != Acao.IGNORAR && !Boolean.TRUE.equals(i.importado().get("aConferir"))) {
				Object a = i.ajustes().containsKey("area") ? i.ajustes().get("area") : i.importado().get("area");
				if (a != null) {
					r.computeIfAbsent(String.valueOf(i.importado().get("clienteNorm")), k -> new LinkedHashSet<>()).add(String.valueOf(a));
				}
			}
		}
		return r;
	}

	/** Area do contrato: ajuste > importada > unica area do cliente. */
	static String areaDoContrato(ItemPrevia contrato, Map<String, Set<String>> areasDosClientes) {
		Object a = contrato.ajustes().containsKey("area") ? contrato.ajustes().get("area") : contrato.importado().get("area");
		if (a != null && !String.valueOf(a).isBlank()) {
			return String.valueOf(a);
		}
		Set<String> doCliente = areasDosClientes.getOrDefault(String.valueOf(contrato.importado().get("clienteNorm")), Set.of());
		return doCliente.size() == 1 ? doCliente.iterator().next() : null;
	}

	/** Area do processo: ajuste > importada > a do contrato com o mesmo numero > unica area do cliente. */
	static AreaAtuacao areaDoProcessoOuNull(ItemPrevia processo, List<ItemPrevia> todos, Map<String, Set<String>> areasDosClientes) {
		Object a = processo.ajustes().containsKey("area") ? processo.ajustes().get("area") : processo.importado().get("area");
		if (a != null && !String.valueOf(a).isBlank()) {
			return AreaAtuacao.valueOf(String.valueOf(a));
		}
		String cnj = texto(processo.importado().get("cnj"));
		for (ItemPrevia c : todos) {
			if (c.tipo() == Tipo.CONTRATO && cnj != null && cnj.equals(texto(c.importado().get("cnj")))) {
				String ac = areaDoContrato(c, areasDosClientes);
				if (ac != null) {
					return AreaAtuacao.valueOf(ac);
				}
			}
		}
		Set<String> doCliente = areasDosClientes.getOrDefault(String.valueOf(processo.importado().get("clienteNorm")), Set.of());
		return doCliente.size() == 1 ? AreaAtuacao.valueOf(doCliente.iterator().next()) : null;
	}

	private void gravarDocumento(UsuarioLogado ator, Cabecalho imp, ItemPrevia i, Ids ids) {
		var m = i.importado();
		Long clienteId = exigir(ids.clientes().get(String.valueOf(m.get("clienteNorm"))), "Cliente do documento nao disponivel");
		java.time.Instant modificado = m.get("modificadoEm") == null ? null : java.time.Instant.parse(String.valueOf(m.get("modificadoEm")));
		var arquivo = new DriveFonte.Arquivo(texto(m.get("driveId")), texto(m.get("nome")), texto(m.get("mime")), modificado, numero(m.get("tamanho")), List.of());
		byte[] conteudo = fontes.para(imp.criadoPor()).baixar(arquivo);
		documentos.anexar(ator.id(), clienteId, TipoDocumento.valueOf(texto(m.get("tipo"))), arquivo.nome(), arquivo.mime(), conteudo, "DRIVE", arquivo.id(), modificado);
	}

	// ---- conversoes

	private static LocalDate data(Object o) {
		return o == null || String.valueOf(o).isBlank() ? null : LocalDate.parse(String.valueOf(o));
	}

	private static AreaAtuacao area(String nome) {
		return nome == null ? null : AreaAtuacao.valueOf(nome);
	}

	private static Set<AreaAtuacao> paraAreas(Set<String> nomes) {
		Set<AreaAtuacao> r = EnumSet.noneOf(AreaAtuacao.class);
		nomes.forEach(n -> r.add(AreaAtuacao.valueOf(n)));
		return r;
	}

	@SuppressWarnings("unchecked")
	private static List<String> lista(Object o) {
		if (o instanceof List<?> l) {
			return ((List<Object>) l).stream().map(String::valueOf).toList();
		}
		return o == null || String.valueOf(o).isBlank() ? List.of() : List.of(String.valueOf(o));
	}

	private static String texto(Object o) {
		return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o);
	}

	private static BigDecimal dec(Object o) {
		return o == null || String.valueOf(o).isBlank() ? null : new BigDecimal(String.valueOf(o));
	}

	private static Long numero(Object o) {
		return o == null ? null : ((Number) (o instanceof Number ? o : new BigDecimal(String.valueOf(o)))).longValue();
	}

	private static Long exigir(Long v, String msg) {
		if (v == null) {
			throw new IllegalStateException(msg);
		}
		return v;
	}

	private static String mensagem(RuntimeException e) {
		String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		return m.length() > 300 ? m.substring(0, 300) : m;
	}
}
