package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import br.com.crispimcampos.gestao.documentos.TipoDocumento;
import br.com.crispimcampos.gestao.google.ConexaoGoogleException;
import br.com.crispimcampos.gestao.importacao.ImportacaoRepositorio.Cabecalho;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Acao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Decisao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Tipo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Importacao da planilha financeira (e, na etapa seguinte, do Drive): gera a previa com divergencias, deixa a socia corrigir e
 * decidir cada conflito, e so grava depois da confirmacao de uma socia. Nada existente e sobrescrito sem decisao explicita.
 * Conteudo financeiro: somente socias, em toda operacao (invariante 1).
 */
@Service
public class ImportacaoService {

	private final ImportacaoRepositorio repo;
	private final AplicadorImportacao aplicador;
	private final DriveFontes fontes;
	private final UsuarioService usuarios;
	private final AuditoriaService auditoria;
	private final JdbcTemplate jdbc;
	private final JsonMapper json;

	ImportacaoService(ImportacaoRepositorio repo, AplicadorImportacao aplicador, DriveFontes fontes, UsuarioService usuarios,
			AuditoriaService auditoria, JdbcTemplate jdbc, JsonMapper json) {
		this.repo = repo;
		this.fontes = fontes;
		this.aplicador = aplicador;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.jdbc = jdbc;
		this.json = json;
	}

	static void exigirSocia(UsuarioLogado u) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("A importacao e restrita as socias");
		}
	}

	/** Le a planilha, gera e guarda a previa. Nada e gravado nos cadastros/financeiro ainda. */
	public ImportacaoDto gerarPreviaPlanilha(UsuarioLogado ator, String nomeArquivo, byte[] arquivo, Long responsavelPadraoId) {
		return gerarPreviaPlanilha(ator, nomeArquivo, arquivo, responsavelPadraoId, "MANUAL");
	}

	/** A planilha financeira que esta no Drive (configurada), baixada com a conta Google da propria socia. */
	public ImportacaoDto gerarPreviaPlanilhaDoDrive(UsuarioLogado ator, Long responsavelPadraoId) {
		exigirSocia(ator);
		byte[] arquivo = doDrive(() -> fontes.baixarPlanilha(ator.id()));
		return gerarPreviaPlanilha(ator, "Planilha financeira (Google Drive)", arquivo, responsavelPadraoId, "MANUAL");
	}

	ImportacaoDto gerarPreviaPlanilha(UsuarioLogado ator, String nomeArquivo, byte[] arquivo, Long responsavelPadraoId, String modo) {
		exigirSocia(ator);
		Long responsavel = responsavelPadraoId == null ? ator.id() : responsavelPadraoId;
		validarResponsavel(responsavel);
		var dados = PlanilhaFinanceira.interpretar(XlsxLeitor.ler(arquivo));
		if (dados.contratos().isEmpty() && dados.parcelas().isEmpty() && dados.despesas().isEmpty()) {
			throw new IllegalArgumentException("Nenhuma aba reconhecida (esperado: Processos, Contas a Receber <mes>, Despesas Mensais)");
		}
		List<ItemPrevia> itens = PreviaPlanilha.montar(dados, new ExistentesJdbc(jdbc), responsavel);
		Long id = repo.criar("PLANILHA", modo, nomeArquivo, responsavel, ator.id());
		repo.salvarItens(id, itens);
		auditoria.registrar(ator.id(), "IMPORTACAO_PREVIA_GERADA", "importacao", String.valueOf(id), "itens=" + itens.size());
		return detalhe(ator, id);
	}

	/** Pastas de clientes do Drive: le os arquivos, extrai dados e gera a previa (nada e gravado ainda). */
	public ImportacaoDto gerarPreviaDrive(UsuarioLogado ator, Long responsavelPadraoId) {
		return gerarPreviaDrive(ator, responsavelPadraoId, "MANUAL");
	}

	ImportacaoDto gerarPreviaDrive(UsuarioLogado ator, Long responsavelPadraoId, String modo) {
		exigirSocia(ator);
		Long responsavel = responsavelPadraoId == null ? ator.id() : responsavelPadraoId;
		validarResponsavel(responsavel);
		DriveFonte fonte = doDrive(() -> fontes.para(ator.id()));
		List<DriveFonte.Arquivo> arquivos = doDrive(fonte::listar);
		List<PreviaDrive.ArquivoLido> lidos = new ArrayList<>();
		for (DriveFonte.Arquivo a : arquivos) {
			TipoDocumento tipo = TipoDocumento.pelNome(a.nome());
			String texto = null;
			boolean leTexto = (tipo == TipoDocumento.CONTRATO_HONORARIOS || tipo == TipoDocumento.PROCURACAO) && a.tamanho() <= PreviaDrive.TAMANHO_MAXIMO
					&& !a.caminho().isEmpty();
			if (leTexto) {
				texto = TextoDeArquivo.ler(a.nome(), doDrive(() -> fonte.baixar(a)));
			}
			lidos.add(new PreviaDrive.ArquivoLido(a, texto));
		}
		List<ItemPrevia> itens = PreviaDrive.montar(lidos, new ExistentesJdbc(jdbc), responsavel);
		if (itens.isEmpty()) {
			throw new IllegalArgumentException("Nenhum arquivo encontrado na pasta de clientes do Drive");
		}
		Long id = repo.criar("DRIVE", modo, "Pasta de clientes (Google Drive)", responsavel, ator.id());
		repo.salvarItens(id, itens);
		auditoria.registrar(ator.id(), "IMPORTACAO_PREVIA_GERADA", "importacao", String.valueOf(id), "drive itens=" + itens.size());
		return detalhe(ator, id);
	}

	/** Resultado de uma sincronizacao diaria. */
	public record ResultadoSincronizacao(String fonte, String situacao, Long importacaoId) {
	}

	/**
	 * Sincronizacao periodica com a planilha e o Drive, em nome de uma socia com a conta Google conectada. Gera a previa; se ela so
	 * traz novidades sem risco (parcelas, despesas e documentos novos de clientes e contratos que ja existem, sem divergencia nem
	 * pendencia), conclui sozinha e fica no historico. Qualquer outra coisa (cliente/contrato/processo novo, conflito, pendencia)
	 * permanece como PREVIA aguardando a confirmacao de uma socia. Sem nenhuma mudanca, apenas registra na auditoria.
	 */
	public List<ResultadoSincronizacao> sincronizar(UsuarioLogado conta) {
		exigirSocia(conta);
		List<ResultadoSincronizacao> r = new ArrayList<>();
		if (fontes.planilhaConfigurada()) {
			r.add(sincronizarUma("PLANILHA", conta, () -> gerarPreviaPlanilha(conta, "Planilha financeira (Google Drive, diario)",
					doDrive(() -> fontes.baixarPlanilha(conta.id())), null, "DIARIA")));
		}
		if (fontes.configurada()) {
			r.add(sincronizarUma("DRIVE", conta, () -> gerarPreviaDrive(conta, null, "DIARIA")));
		}
		return r;
	}

	private ResultadoSincronizacao sincronizarUma(String fonte, UsuarioLogado conta, java.util.function.Supplier<ImportacaoDto> gerar) {
		try {
			ImportacaoDto d = gerar.get();
			List<ItemPrevia> novidades = d.itens().stream().filter(i -> i.acao() == Acao.CRIAR || (i.acao() == Acao.CONFLITO && !repo.conflitoJaMantido(i))).toList();
			if (novidades.isEmpty()) {
				repo.cancelar(d.id(), conta.id());
				auditoria.registrar(conta.id(), "IMPORTACAO_SINCRONIZACAO_SEM_MUDANCA", "importacao", String.valueOf(d.id()), fonte);
				return new ResultadoSincronizacao(fonte, "SEM_MUDANCA", d.id());
			}
			boolean seguro = d.pendencias().isEmpty() && novidades.stream().noneMatch(i -> i.acao() == Acao.CONFLITO)
					&& novidades.stream().allMatch(i -> i.tipo() == Tipo.PARCELA || i.tipo() == Tipo.DESPESA || i.tipo() == Tipo.DOCUMENTO)
					&& novidades.stream().filter(i -> i.tipo() == Tipo.PARCELA).allMatch(i -> i.divergencias().stream().noneMatch(x -> x.contains("sem contrato") || x.contains("contrato avulso")));
			if (seguro) {
				confirmar(conta, d.id());
				return new ResultadoSincronizacao(fonte, "APLICADA", d.id());
			}
			auditoria.registrar(conta.id(), "IMPORTACAO_SINCRONIZACAO_PENDENTE", "importacao", String.valueOf(d.id()), fonte);
			return new ResultadoSincronizacao(fonte, "AGUARDA_CONFIRMACAO", d.id());
		} catch (RuntimeException e) {
			auditoria.registrar(conta.id(), "IMPORTACAO_SINCRONIZACAO_FALHOU", "importacao", null, fonte + ": " + e.getClass().getSimpleName());
			return new ResultadoSincronizacao(fonte, "FALHOU", null);
		}
	}

	/** Informacao para a tela: o que esta configurado e se a conta da socia pode ler o Drive. */
	public Map<String, Object> statusDrive(UsuarioLogado ator) {
		exigirSocia(ator);
		return Map.of("pastaClientesConfigurada", fontes.configurada(), "planilhaConfigurada", fontes.planilhaConfigurada(), "contaConectadaAoDrive",
				fontes.disponivel(ator.id()));
	}

	private <T> T doDrive(java.util.function.Supplier<T> acao) {
		try {
			return acao.get();
		} catch (ConexaoGoogleException e) {
			throw new IllegalArgumentException(e.getMessage());
		} catch (IllegalStateException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}

	public List<ImportacaoDto> listar(UsuarioLogado ator) {
		exigirSocia(ator);
		return repo.listar().stream().map(c -> montar(c, List.of(), false)).toList();
	}

	public ImportacaoDto detalhe(UsuarioLogado ator, Long id) {
		exigirSocia(ator);
		Cabecalho c = cabecalho(id);
		return montar(c, repo.itens(id), true);
	}

	/** Correcao/decisao de um item da previa (area, vencimento, valor, aplicar ou manter). */
	public ImportacaoDto decidir(UsuarioLogado ator, Long importacaoId, Long itemId, Decisao decisao, Map<String, Object> ajustes) {
		exigirSocia(ator);
		exigirPrevia(importacaoId);
		ItemPrevia item = repo.item(importacaoId, itemId).orElseThrow(() -> new IllegalArgumentException("Item nao encontrado"));
		if (item.acao() == Acao.IGNORAR || item.acao() == Acao.SEM_MUDANCA) {
			throw new IllegalArgumentException("Este item nao tem decisao a tomar");
		}
		Map<String, Object> novos = new LinkedHashMap<>(item.ajustes());
		if (ajustes != null) {
			ajustes.forEach((k, v) -> {
				if (CAMPOS_AJUSTAVEIS.contains(k)) {
					if (v == null || String.valueOf(v).isBlank()) {
						novos.remove(k);
					} else {
						novos.put(k, v);
					}
				}
			});
		}
		repo.decidir(itemId, decisao != null ? decisao : item.decisao(), novos);
		return detalhe(ator, importacaoId);
	}

	/** Mesma decisao para todos os itens de uma acao (ex.: aplicar todos os conflitos de parcelas). */
	public ImportacaoDto decidirEmLote(UsuarioLogado ator, Long importacaoId, Acao acao, Tipo tipo, Decisao decisao) {
		exigirSocia(ator);
		exigirPrevia(importacaoId);
		if (acao != Acao.CRIAR && acao != Acao.CONFLITO) {
			throw new IllegalArgumentException("So itens a criar ou em conflito aceitam decisao");
		}
		repo.decidirTodos(importacaoId, acao, tipo, decisao);
		return detalhe(ator, importacaoId);
	}

	/** Grava tudo o que foi aprovado. Recusa se ainda houver pendencia (area faltando, vencimento invalido etc.). */
	public ImportacaoDto confirmar(UsuarioLogado ator, Long importacaoId) {
		exigirSocia(ator);
		Cabecalho c = exigirPrevia(importacaoId);
		List<ItemPrevia> itens = repo.itens(importacaoId);
		List<String> pend = Pendencias.de(itens);
		if (!pend.isEmpty()) {
			throw new PendenciaDeImportacaoException(pend);
		}
		Map<String, Object> relatorio = aplicador.aplicar(ator, c, itens);
		repo.concluir(importacaoId, ator.id(), repo.escrever(relatorio));
		auditoria.registrar(ator.id(), "IMPORTACAO_CONFIRMADA", "importacao", String.valueOf(importacaoId), resumoAuditoria(relatorio));
		return detalhe(ator, importacaoId);
	}

	public ImportacaoDto cancelar(UsuarioLogado ator, Long importacaoId) {
		exigirSocia(ator);
		exigirPrevia(importacaoId);
		repo.cancelar(importacaoId, ator.id());
		auditoria.registrar(ator.id(), "IMPORTACAO_CANCELADA", "importacao", String.valueOf(importacaoId), null);
		return detalhe(ator, importacaoId);
	}

	// ---- internos

	static final java.util.Set<String> CAMPOS_AJUSTAVEIS = java.util.Set.of("area", "areas", "vencimento", "valor", "valorTotal", "tribunal", "sobrescrever");

	private Cabecalho cabecalho(Long id) {
		return repo.cabecalho(id).orElseThrow(() -> new IllegalArgumentException("Importacao nao encontrada"));
	}

	private Cabecalho exigirPrevia(Long id) {
		Cabecalho c = cabecalho(id);
		if (!"PREVIA".equals(c.status())) {
			throw new IllegalArgumentException("Esta importacao ja foi " + ("CONCLUIDA".equals(c.status()) ? "concluida" : "cancelada"));
		}
		return c;
	}

	private void validarResponsavel(Long id) {
		UsuarioDto u;
		try {
			u = usuarios.buscar(id);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Responsavel padrao nao encontrado");
		}
		if (!u.ativo() || (u.perfil() != Perfil.ADVOGADO && u.perfil() != Perfil.SOCIA)) {
			throw new IllegalArgumentException("O responsavel padrao deve ser advogado ou socia ativo");
		}
	}

	private ImportacaoDto montar(Cabecalho c, List<ItemPrevia> itens, boolean completo) {
		Map<String, Map<String, Long>> cont = new LinkedHashMap<>();
		long div = 0;
		for (ItemPrevia i : itens) {
			cont.computeIfAbsent(i.tipo().name(), k -> new LinkedHashMap<>()).merge(i.acao().name(), 1L, Long::sum);
			if (!i.divergencias().isEmpty() && i.acao() != Acao.IGNORAR) {
				div++;
			}
		}
		Map<String, Object> relatorio = null;
		if (c.relatorio() != null) {
			relatorio = json.readValue(c.relatorio(), new TypeReference<Map<String, Object>>() {
			});
		}
		List<String> pend = completo && "PREVIA".equals(c.status()) ? Pendencias.de(itens) : List.of();
		return new ImportacaoDto(c.id(), c.origem(), c.modo(), c.arquivoNome(), c.status(), c.criadoEm(), c.confirmadoEm(), c.confirmadoPor(), cont, div,
				pend, relatorio, completo ? itens : List.of());
	}

	private static String resumoAuditoria(Map<String, Object> relatorio) {
		Object tot = relatorio.get("totais");
		return tot == null ? null : tot.toString();
	}

	/** Pendencias que impedem confirmar (a previa as mostra). */
	static final class Pendencias {

		private Pendencias() {
		}

		static List<String> de(List<ItemPrevia> itens) {
			List<String> r = new ArrayList<>();
			Map<String, java.util.Set<String>> areasDoCliente = AplicadorImportacao.areasEfetivasDosClientes(itens);
			Map<String, ItemPrevia> contratos = itens.stream().filter(i -> i.tipo() == Tipo.CONTRATO)
					.collect(Collectors.toMap(ItemPrevia::chave, i -> i, (a, b) -> a));
			for (ItemPrevia i : itens) {
				if (i.acao() == Acao.IGNORAR || !i.aplicar()) {
					continue;
				}
				switch (i.tipo()) {
					case CLIENTE -> {
						if (i.acao() == Acao.CRIAR && areasDoCliente.getOrDefault(String.valueOf(i.importado().get("nomeNormalizado")), java.util.Set.of()).isEmpty()) {
							r.add("Cliente \"" + i.rotulo() + "\": escolha a area de atuacao");
						}
					}
					case CONTRATO -> {
						if (i.acao() == Acao.CRIAR && AplicadorImportacao.areaDoContrato(i, areasDoCliente) == null) {
							r.add("Contrato \"" + i.rotulo() + "\": escolha a area de atuacao");
						}
					}
					case PARCELA -> {
						if (i.valor("vencimento") == null) {
							r.add("Parcela \"" + i.rotulo() + "\": informe a data de vencimento");
						}
						ItemPrevia c = contratos.get(String.valueOf(i.importado().get("contratoChave")));
						if (c != null && c.acao() == Acao.CRIAR && !c.aplicar()) {
							r.add("Parcela \"" + i.rotulo() + "\": depende de um contrato que nao sera importado");
						}
					}
					case PROCESSO -> {
						if (i.acao() == Acao.CRIAR && AplicadorImportacao.areaDoProcessoOuNull(i, itens, areasDoCliente) == null) {
							r.add("Processo \"" + i.rotulo() + "\": escolha a area de atuacao");
						}
					}
					default -> {
					}
				}
			}
			return r;
		}
	}
}
