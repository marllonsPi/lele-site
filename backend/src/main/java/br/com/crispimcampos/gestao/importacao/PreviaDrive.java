package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.documentos.TipoDocumento;
import br.com.crispimcampos.gestao.importacao.ExtratorDocumento.Dados;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Acao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Tipo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Previa da importacao das pastas de clientes do Drive: um cliente por pasta de primeiro nivel; contratos de honorarios (valor,
 * parcelas, assinatura), numeros de processo (CNJ valido), documentos e dados cadastrais. Tudo extraido e marcado "conferir".
 */
final class PreviaDrive {

	/** Arquivo ja lido: {@code texto} so existe para os formatos e tipos de que se extrai dado (contrato, procuracao, documentos). */
	record ArquivoLido(DriveFonte.Arquivo arquivo, String texto) {
	}

	static final long TAMANHO_MAXIMO = 20L * 1024 * 1024;

	private final Existentes existentes;
	private final Long responsavelPadraoId;
	private final List<ItemPrevia> itens = new ArrayList<>();

	private PreviaDrive(Existentes existentes, Long responsavelPadraoId) {
		this.existentes = existentes;
		this.responsavelPadraoId = responsavelPadraoId;
	}

	static List<ItemPrevia> montar(List<ArquivoLido> arquivos, Existentes existentes, Long responsavelPadraoId) {
		PreviaDrive p = new PreviaDrive(existentes, responsavelPadraoId);
		Map<String, List<ArquivoLido>> porPasta = new LinkedHashMap<>();
		List<ArquivoLido> soltos = new ArrayList<>();
		for (ArquivoLido a : arquivos) {
			if (a.arquivo().caminho().isEmpty()) {
				soltos.add(a);
			} else {
				porPasta.computeIfAbsent(a.arquivo().caminho().get(0), k -> new ArrayList<>()).add(a);
			}
		}
		porPasta.forEach(p::cliente);
		soltos.forEach(p::solto);
		return List.copyOf(p.itens);
	}

	private void solto(ArquivoLido a) {
		List<String> cnjs = a.texto() == null ? ExtratorDocumento.processos(a.arquivo().nome()) : ExtratorDocumento.processos(a.arquivo().nome() + "\n" + a.texto());
		if (cnjs.isEmpty()) {
			adicionar(Tipo.DOCUMENTO, "DRIVE|" + a.arquivo().id(), a.arquivo().nome(), Acao.IGNORAR,
					List.of("Arquivo solto na raiz do Drive, fora de uma pasta de cliente"), Map.of("arquivo", a.arquivo().nome()), null);
			return;
		}
		for (String cnj : cnjs) {
			adicionar(Tipo.PROCESSO, "DRIVE-SOLTO|" + cnj, cnj, Acao.IGNORAR,
					List.of("Processo sem cliente vinculado: o arquivo \"" + a.arquivo().nome() + "\" esta fora de uma pasta de cliente"), Map.of("cnj", cnj), null);
		}
	}

	private void cliente(String pasta, List<ArquivoLido> arquivos) {
		String docPasta = ExtratorDocumento.documento(pasta);
		String nome = pasta.replaceAll("[\\d.\\-/]{11,18}", "").replaceAll("[\\s\\-_–]+$", "").replaceAll("^[\\s\\-_–]+", "").trim();
		if (nome.isEmpty()) {
			nome = pasta;
		}
		String norm = Texto.normalizar(nome);

		// dados extraidos: contrato primeiro, depois procuracao, depois o resto
		List<ArquivoLido> ordenados = new ArrayList<>(arquivos);
		ordenados.sort(java.util.Comparator.comparingInt(a -> prioridade(TipoDocumento.pelNome(a.arquivo().nome()))));
		String doc = docPasta, rg = null, civil = null, endereco = null, telefone = null, email = null;
		Set<String> cnjs = new LinkedHashSet<>();
		Map<String, Dados> dadosPorArquivo = new LinkedHashMap<>();
		for (ArquivoLido a : ordenados) {
			cnjs.addAll(ExtratorDocumento.processos(a.arquivo().nome()));
			if (a.texto() == null) {
				continue;
			}
			Dados d = ExtratorDocumento.extrair(a.texto());
			dadosPorArquivo.put(a.arquivo().id(), d);
			doc = doc != null ? doc : d.documento();
			rg = rg != null ? rg : d.rg();
			civil = civil != null ? civil : d.estadoCivil();
			endereco = endereco != null ? endereco : d.endereco();
			telefone = telefone != null ? telefone : d.telefone();
			email = email != null ? email : d.email();
			cnjs.addAll(d.processos());
		}

		Optional<Map<String, Object>> ex = existentes.cliente(doc, norm);
		Map<String, Object> imp = PreviaPlanilha.mapa("nome", nome, "nomeNormalizado", norm, "documento", doc, "rg", rg, "estadoCivil", civil, "endereco", endereco,
				"telefone", telefone, "email", email, "areas", List.of());
		List<String> div = new ArrayList<>();
		div.add("Dados extraidos automaticamente dos arquivos do Drive: conferir");
		Acao acao;
		if (ex.isEmpty()) {
			acao = Acao.CRIAR;
			if (doc == null) {
				div.add("Sem CPF/CNPJ em nenhum arquivo da pasta: cliente identificado so pelo nome, conferir manualmente");
			}
			div.add("Area de atuacao pendente: escolha na previa antes de confirmar");
		} else {
			acao = Acao.SEM_MUDANCA;
			Map<String, Object> e = ex.get();
			for (String campo : List.of("documento", "rg", "estadoCivil", "endereco", "telefone", "email")) {
				Object novo = imp.get(campo);
				Object atual = e.get(campo);
				if (novo == null) {
					continue;
				}
				if (atual == null || String.valueOf(atual).isBlank()) {
					acao = Acao.CONFLITO;
					div.add(rotuloCampo(campo) + ": vazio no sistema, o Drive traz \"" + novo + "\" (sera preenchido se aplicar)");
				} else if (!PreviaPlanilha.iguais(novo, atual)) {
					acao = Acao.CONFLITO;
					div.add(rotuloCampo(campo) + ": sistema \"" + atual + "\" x Drive \"" + novo + "\" (mantido o do sistema, salvo se escolher sobrescrever)");
				}
			}
			if (doc == null && e.get("documento") == null) {
				div.add("Sem CPF/CNPJ no sistema nem no Drive: vinculado pelo nome, conferir se e a mesma pessoa");
			}
		}
		adicionar(Tipo.CLIENTE, "CLIENTE|" + norm, nome, acao, div, imp, ex.orElse(null));

		List<Map<String, Object>> contratosDoSistema = ex.isPresent() ? existentes.contratosDoCliente(((Number) ex.get().get("id")).longValue()) : List.of();
		for (ArquivoLido a : arquivos) {
			documento(a, norm, dadosPorArquivo.get(a.arquivo().id()));
		}
		for (ArquivoLido a : arquivos) {
			if (TipoDocumento.pelNome(a.arquivo().nome()) == TipoDocumento.CONTRATO_HONORARIOS && dadosPorArquivo.containsKey(a.arquivo().id())) {
				contrato(a, nome, norm, dadosPorArquivo.get(a.arquivo().id()), contratosDoSistema);
			}
		}
		for (String cnj : cnjs) {
			processo(cnj, nome, norm);
		}
	}

	private void documento(ArquivoLido a, String norm, Dados dados) {
		TipoDocumento tipo = TipoDocumento.pelNome(a.arquivo().nome());
		List<String> div = new ArrayList<>();
		Map<String, Object> imp = PreviaPlanilha.mapa("clienteNorm", norm, "nome", a.arquivo().nome(), "tipo", tipo.name(), "mime", a.arquivo().mime(),
				"tamanho", a.arquivo().tamanho(), "driveId", a.arquivo().id(), "modificadoEm", a.arquivo().modificadoEm() == null ? null : a.arquivo().modificadoEm().toString(),
				"restritoSocias", tipo == TipoDocumento.CONTRATO_HONORARIOS, "pasta", String.join(" / ", a.arquivo().caminho()));
		if (a.arquivo().tamanho() > TAMANHO_MAXIMO) {
			adicionar(Tipo.DOCUMENTO, "DRIVE|" + a.arquivo().id(), a.arquivo().nome(), Acao.IGNORAR, List.of("Arquivo maior que 20 MB: nao importado"), imp, null);
			return;
		}
		if (tipo == TipoDocumento.CONTRATO_HONORARIOS) {
			if (dados == null) {
				div.add("Contrato sem texto legivel (digitalizado ou formato nao suportado): valor, parcelas e assinatura nao extraidos; o arquivo sera so anexado");
			}
			div.add("Contrato de honorarios: anexado como restrito as socias");
		}
		Optional<Map<String, Object>> ex = existentes.documentoPorDrive(a.arquivo().id());
		if (ex.isPresent()) {
			Object antes = ex.get().get("driveModificadoEm");
			if (a.arquivo().modificadoEm() != null && antes != null && a.arquivo().modificadoEm().isAfter(Instant.parse(String.valueOf(antes)).plusSeconds(1))) {
				div.add("Arquivo alterado no Drive depois da importacao anterior (a versao ja importada foi mantida)");
			}
			adicionar(Tipo.DOCUMENTO, "DRIVE|" + a.arquivo().id(), a.arquivo().nome(), Acao.SEM_MUDANCA, div, imp, ex.get());
		} else {
			adicionar(Tipo.DOCUMENTO, "DRIVE|" + a.arquivo().id(), a.arquivo().nome(), Acao.CRIAR, div, imp, null);
		}
	}

	private void contrato(ArquivoLido a, String nome, String norm, Dados d, List<Map<String, Object>> doSistema) {
		List<String> div = new ArrayList<>();
		String aviso = "Dados do contrato extraidos automaticamente: conferir valor, parcelas e data";
		div.add(aviso);
		if (d.valorTotal() == null) {
			div.add("Valor dos honorarios nao encontrado no texto do contrato");
		}
		String cnj = d.processos().isEmpty() ? null : d.processos().get(0);
		Map<String, Object> imp = PreviaPlanilha.mapa("clienteNorm", norm, "clienteNome", nome, "cnj", cnj, "tipoAcao", "Contrato do Drive", "area", null,
				"valorTotal", d.valorTotal() == null ? BigDecimal.ZERO : d.valorTotal(), "formaPagamento", d.formaPagamento(), "numeroParcelas", d.parcelas(),
				"dataAssinatura", d.dataAssinatura() == null ? null : d.dataAssinatura().toString(), "entrada", d.entrada(), "valorParcela", d.valorParcela(),
				"descricao", "Contrato de honorarios — " + a.arquivo().nome(), "aConferir", false, "driveArquivoId", a.arquivo().id(), "percentualExito", null);
		Map<String, Object> par = null;
		List<Map<String, Object>> daCnj = cnj == null ? List.of() : doSistema.stream().filter(c -> d.processos().contains(String.valueOf(c.get("cnj")).trim())).toList();
		if (daCnj.size() == 1) {
			par = daCnj.get(0);
		} else if (doSistema.size() == 1) {
			par = doSistema.get(0);
		}
		String chave = "DRIVE|" + norm + "|" + a.arquivo().id();
		if (par == null) {
			if (!doSistema.isEmpty()) {
				div.add("O contrato nao casou com nenhum dos " + doSistema.size() + " contratos do cliente: sera criado separado (a conferir)");
				imp.put("aConferir", true);
			}
			div.add("Area de atuacao pendente: escolha na previa antes de confirmar");
			adicionar(Tipo.CONTRATO, chave, nome + " — contrato do Drive", Acao.CRIAR, div, imp, null);
			return;
		}
		// mesmo contrato ja existente (planilha ou cadastro): compara e so preenche o que falta
		Acao acao = Acao.SEM_MUDANCA;
		BigDecimal valorSistema = par.get("valorTotal") == null ? null : new BigDecimal(String.valueOf(par.get("valorTotal")));
		if (d.valorTotal() != null && valorSistema != null && valorSistema.signum() > 0 && d.valorTotal().compareTo(valorSistema) != 0) {
			acao = Acao.CONFLITO;
			div.add("Valor do contrato no Drive (R$ " + d.valorTotal() + ") diferente do valor no sistema/planilha (R$ " + valorSistema + ")");
		} else if (d.valorTotal() != null && (valorSistema == null || valorSistema.signum() == 0)) {
			acao = Acao.CONFLITO;
			div.add("Valor vazio no sistema; o Drive traz R$ " + d.valorTotal());
		}
		for (String[] c : new String[][] {{"dataAssinatura", "data de assinatura"}, {"numeroParcelas", "numero de parcelas"}, {"formaPagamento", "forma de pagamento"}}) {
			if (imp.get(c[0]) != null && par.get(c[0]) == null) {
				acao = Acao.CONFLITO;
				div.add("Preenche " + c[1] + " (vazio no sistema)");
			}
		}
		div.add("Contrato ja cadastrado: o do Drive complementa o existente, sem criar outro");
		adicionar(Tipo.CONTRATO, chave, nome + " — contrato do Drive", acao, div, imp, par);
	}

	private void processo(String cnj, String nome, String norm) {
		List<String> div = new ArrayList<>();
		String tribunal = TribunalCnj.de(cnj);
		div.add(tribunal == null ? "Tribunal nao identificado pelo numero: informe na previa" : "Tribunal inferido do numero CNJ: " + tribunal);
		Map<String, Object> imp = PreviaPlanilha.mapa("cnj", cnj, "clienteNorm", norm, "clienteNome", nome, "tribunal", tribunal, "classe", null, "situacao", null,
				"valorCausa", null, "area", null, "advogadoId", responsavelPadraoId);
		Optional<Map<String, Object>> ex = existentes.processo(cnj);
		Acao acao = Acao.CRIAR;
		if (ex.isPresent()) {
			Object doutro = ex.get().get("clienteNorm");
			if (doutro != null && !doutro.equals(norm)) {
				acao = Acao.CONFLITO;
				div.add("Processo ja cadastrado para outro cliente (" + doutro + "): conferir o vinculo");
			} else {
				acao = Acao.SEM_MUDANCA;
			}
		} else {
			div.add("Area de atuacao do processo: derivada do cliente/contrato; confira na previa");
		}
		adicionar(Tipo.PROCESSO, "CNJ|" + cnj, cnj, acao, div, imp, ex.orElse(null));
	}

	private static int prioridade(TipoDocumento t) {
		return switch (t) {
			case CONTRATO_HONORARIOS -> 0;
			case PROCURACAO -> 1;
			case DOCUMENTO_PESSOAL -> 2;
			default -> 3;
		};
	}

	private static String rotuloCampo(String campo) {
		return switch (campo) {
			case "documento" -> "CPF/CNPJ";
			case "rg" -> "RG";
			case "estadoCivil" -> "Estado civil";
			case "endereco" -> "Endereco";
			case "telefone" -> "Telefone";
			default -> "E-mail";
		};
	}

	private void adicionar(Tipo tipo, String chave, String rotulo, Acao acao, List<String> div, Map<String, Object> imp, Map<String, Object> ex) {
		itens.add(new ItemPrevia(null, itens.size(), tipo, chave, rotulo, acao, List.copyOf(div), imp, ex, null, Map.of(), null, null));
	}
}
