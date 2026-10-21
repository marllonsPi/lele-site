package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.financeiro.FinanceiroImportacao;
import br.com.crispimcampos.gestao.financeiro.FinanceiroImportacao.DadosContrato;
import br.com.crispimcampos.gestao.financeiro.FinanceiroImportacao.DadosParcela;
import br.com.crispimcampos.gestao.financeiro.Parcelamento;
import br.com.crispimcampos.gestao.honorarios.PropostaDtos.Fechamento;
import br.com.crispimcampos.gestao.honorarios.PropostaDtos.ItemProposta;
import br.com.crispimcampos.gestao.honorarios.PropostaDtos.PropostaDto;
import br.com.crispimcampos.gestao.honorarios.SugestaoHonorarios.Servico;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proposta de honorarios do atendimento. Sugestao para a socia ou o advogado que conduz o atendimento (ATD); o fechamento guarda o
 * valor sugerido e o efetivamente fechado; abaixo do minimo da OAB exige justificativa (registrada para as socias); ao assinar, as
 * parcelas entram no financeiro por area. Assistente e estagiario nao tem acesso. Valores financeiros so aparecem aqui para quem
 * negocia (e para as socias): invariante 1.
 */
@Service
public class PropostaService {

	private final JdbcTemplate jdbc;
	private final SugestaoService sugestoes;
	private final FinanceiroImportacao financeiro;
	private final CadastroService cadastros;
	private final AuditoriaService auditoria;

	PropostaService(JdbcTemplate jdbc, SugestaoService sugestoes, FinanceiroImportacao financeiro, CadastroService cadastros, AuditoriaService auditoria) {
		this.jdbc = jdbc;
		this.sugestoes = sugestoes;
		this.financeiro = financeiro;
		this.cadastros = cadastros;
		this.auditoria = auditoria;
	}

	// ---- acesso

	private record Atendimento(Long clienteId, Long atendenteId) {
	}

	private static void exigirSociaOuAdvogado(UsuarioLogado u) {
		if (u.perfil() != Perfil.SOCIA && u.perfil() != Perfil.ADVOGADO) {
			throw new SecurityException("Sem acesso aos honorarios");
		}
	}

	/** Atendimento (ATD) conduzido por quem pede (ou qualquer um, para socia). */
	private Atendimento atendimento(UsuarioLogado u, Long agendamentoId) {
		exigirSociaOuAdvogado(u);
		var l = jdbc.queryForList("select cliente_id, advogado_atendente_id from agendamento where id = ? and tipo = 'ATD'", agendamentoId);
		if (l.isEmpty()) {
			throw new PropostaNaoEncontradaException("Atendimento nao encontrado");
		}
		Long atendente = l.get(0).get("advogado_atendente_id") == null ? null : ((Number) l.get(0).get("advogado_atendente_id")).longValue();
		if (u.perfil() != Perfil.SOCIA && !u.id().equals(atendente)) {
			throw new PropostaNaoEncontradaException("Atendimento nao encontrado");
		}
		return new Atendimento(((Number) l.get(0).get("cliente_id")).longValue(), atendente);
	}

	private Map<String, Object> propostaVisivel(UsuarioLogado u, Long id) {
		exigirSociaOuAdvogado(u);
		var l = jdbc.queryForList("select * from proposta_honorarios where id = ?", id);
		if (l.isEmpty() || (u.perfil() != Perfil.SOCIA && !u.id().equals(((Number) l.get(0).get("advogado_id")).longValue()))) {
			throw new PropostaNaoEncontradaException("Proposta nao encontrada");
		}
		return l.get(0);
	}

	// ---- sugestao

	/** Calcula e guarda a sugestao do atendimento: pede o(s) servico(s) pretendido(s) e a seccional do local do servico. */
	@Transactional
	public PropostaDto sugerirNoAtendimento(UsuarioLogado u, Long agendamentoId, Seccional seccional, List<String> chaves) {
		Atendimento a = atendimento(u, agendamentoId);
		var existente = jdbc.queryForList("select id, status from proposta_honorarios where agendamento_id = ?", agendamentoId);
		Long id;
		if (existente.isEmpty()) {
			id = jdbc.queryForObject("insert into proposta_honorarios (cliente_id, agendamento_id, advogado_id, seccional) values (?, ?, ?, ?) returning id", Long.class, a.clienteId(),
					agendamentoId, a.atendenteId() != null ? a.atendenteId() : u.id(), seccional.name());
		} else {
			id = ((Number) existente.get(0).get("id")).longValue();
			if (!"ABERTA".equals(existente.get(0).get("status"))) {
				throw new IllegalArgumentException("A proposta deste atendimento ja foi encerrada");
			}
		}
		return recalcular(u, id, seccional, chaves);
	}

	/** Proposta sem agendamento (cadastro de cliente novo): somente socias. */
	@Transactional
	public PropostaDto sugerirParaCliente(UsuarioLogado u, Long clienteId, Seccional seccional, List<String> chaves) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("Somente as socias criam proposta fora de um atendimento");
		}
		if (!cadastros.clienteExisteAtivo(clienteId)) {
			throw new IllegalArgumentException("Cliente nao encontrado ou desativado");
		}
		Long id = jdbc.queryForObject("insert into proposta_honorarios (cliente_id, advogado_id, seccional) values (?, ?, ?) returning id", Long.class, clienteId, u.id(), seccional.name());
		return recalcular(u, id, seccional, chaves);
	}

	/** Refaz os servicos e totais da proposta ainda aberta (ex.: trocar o servico ou somar outra demanda). */
	@Transactional
	public PropostaDto recalcular(UsuarioLogado u, Long id, Seccional seccional, List<String> chaves) {
		var p = propostaVisivel(u, id);
		if (!"ABERTA".equals(p.get("status"))) {
			throw new IllegalArgumentException("A proposta ja foi encerrada");
		}
		var r = sugestoes.calcular(seccional, chaves);
		jdbc.update("delete from proposta_item where proposta_id = ?", id);
		for (var l : r.quadro().linhas()) {
			Servico s = l.servico();
			jdbc.update("insert into proposta_item (proposta_id, chave_servico, area, tipo_servico, fase, item_oab_id, item_escritorio_id, minimo_oab, valor_tabela, valor_sugerido, exito_texto, "
					+ "exito_min, exito_max) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, s.chave(), s.area(), s.tipo(), s.fase(), s.itemOabId(), s.itemEscritorioId(), l.minimoOab(),
					l.valorTabela(), l.valorSugerido(), s.exitoTexto(), s.exitoMin(), s.exitoMax());
		}
		jdbc.update("update proposta_honorarios set seccional = ?, valor_minimo_oab = ?, valor_sugerido = ?, desconto_pacote = ?, atualizado_em = now() where id = ?", seccional.name(),
				r.quadro().minimoOabTotal(), r.quadro().valorSugeridoTotal(), r.quadro().descontoPacote(), id);
		auditoria.registrar(u.id(), "PROPOSTA_SUGERIDA", "proposta_honorarios", String.valueOf(id), "servicos=" + chaves.size());
		return montar(id, r.tabelaOabVigente());
	}

	@Transactional(readOnly = true)
	public PropostaDto buscar(UsuarioLogado u, Long id) {
		propostaVisivel(u, id);
		return montar(id, null);
	}

	/** Proposta do atendimento, ou null se ainda nao ha. */
	@Transactional(readOnly = true)
	public PropostaDto doAtendimento(UsuarioLogado u, Long agendamentoId) {
		atendimento(u, agendamentoId);
		var l = jdbc.queryForList("select id from proposta_honorarios where agendamento_id = ?", agendamentoId);
		return l.isEmpty() ? null : montar(((Number) l.get(0).get("id")).longValue(), null);
	}

	// ---- fechamento

	/**
	 * Registra o valor efetivamente fechado. Abaixo do minimo da OAB: exige tipo e texto da justificativa (permuta, hipossuficiencia,
	 * pro bono, pacote...), e o caso aparece no relatorio das socias.
	 */
	@Transactional
	public PropostaDto fechar(UsuarioLogado u, Long id, Fechamento f) {
		var p = propostaVisivel(u, id);
		if ("FECHADA".equals(p.get("status")) && p.get("data_assinatura") != null) {
			throw new IllegalArgumentException("Contrato ja assinado: o valor nao pode mais ser alterado");
		}
		if (f.valorFechado() == null || f.valorFechado().signum() < 0) {
			throw new IllegalArgumentException("Informe o valor fechado");
		}
		long itens = jdbc.queryForObject("select count(*) from proposta_item where proposta_id = ?", Long.class, id);
		if (itens == 0) {
			throw new IllegalArgumentException("Escolha o servico antes de fechar a proposta");
		}
		BigDecimal minimo = (BigDecimal) p.get("valor_minimo_oab");
		boolean abaixo = f.valorFechado().compareTo(minimo) < 0;
		String tipo = f.justificativaTipo() == null || f.justificativaTipo().isBlank() ? null : f.justificativaTipo().trim();
		String texto = f.justificativa() == null || f.justificativa().isBlank() ? null : f.justificativa().trim();
		if (abaixo && (tipo == null || texto == null)) {
			throw new IllegalArgumentException("Valor abaixo do minimo da OAB (diferenca de R$ " + minimo.subtract(f.valorFechado()).setScale(2, RoundingMode.HALF_UP).toPlainString()
					+ "): informe o motivo (permuta, hipossuficiencia, pro bono, pacote...) e a justificativa para salvar");
		}
		if (!abaixo) {
			tipo = null;
			texto = null;
		}
		validarPagamento(f);
		jdbc.update("update proposta_honorarios set status = 'FECHADA', valor_fechado = ?, justificativa_tipo = ?, justificativa = ?, entrada = ?, parcelas = ?, primeiro_vencimento = ?, "
				+ "forma_pagamento = ?, fechado_em = coalesce(fechado_em, now()), atualizado_em = now() where id = ?", f.valorFechado().setScale(2, RoundingMode.HALF_UP), tipo, texto,
				f.entrada(), f.parcelas(), f.primeiroVencimento() == null ? null : java.sql.Date.valueOf(f.primeiroVencimento()), f.formaPagamento(), id);
		auditoria.registrar(u.id(), abaixo ? "PROPOSTA_FECHADA_ABAIXO_OAB" : "PROPOSTA_FECHADA", "proposta_honorarios", String.valueOf(id), abaixo ? "tipo=" + tipo : null);
		return montar(id, null);
	}

	/** A reuniao aconteceu mas o cliente nao fechou: conta no relatorio de conversao. */
	@Transactional
	public PropostaDto naoFechada(UsuarioLogado u, Long id) {
		var p = propostaVisivel(u, id);
		if ("FECHADA".equals(p.get("status"))) {
			throw new IllegalArgumentException("A proposta ja foi fechada");
		}
		jdbc.update("update proposta_honorarios set status = 'NAO_FECHADA', atualizado_em = now() where id = ?", id);
		auditoria.registrar(u.id(), "PROPOSTA_NAO_FECHADA", "proposta_honorarios", String.valueOf(id), null);
		return montar(id, null);
	}

	private static void validarPagamento(Fechamento f) {
		BigDecimal entrada = f.entrada() == null ? BigDecimal.ZERO : f.entrada();
		if (entrada.signum() < 0 || entrada.compareTo(f.valorFechado()) > 0) {
			throw new IllegalArgumentException("A entrada deve estar entre zero e o valor fechado");
		}
		int parcelas = f.parcelas() == null ? 0 : f.parcelas();
		if (parcelas < 0 || parcelas > 120) {
			throw new IllegalArgumentException("Numero de parcelas invalido (0 a 120)");
		}
		if (parcelas > 0 && entrada.compareTo(f.valorFechado()) >= 0) {
			throw new IllegalArgumentException("Com parcelas, a entrada deve ser menor que o valor fechado");
		}
		if (parcelas > 0 && f.primeiroVencimento() == null) {
			throw new IllegalArgumentException("Informe o vencimento da primeira parcela");
		}
	}

	// ---- assinatura: lanca contrato e parcelas por area

	/** Ao assinar, lanca no financeiro um contrato por area (com entrada e parcelas), vinculados ao cliente. Somente socias. */
	@Transactional
	public PropostaDto assinar(UsuarioLogado u, Long id, LocalDate dataAssinatura) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("O lancamento no financeiro e restrito as socias");
		}
		var p = propostaVisivel(u, id);
		if (!"FECHADA".equals(p.get("status"))) {
			throw new IllegalArgumentException("Feche a proposta (valor negociado) antes de assinar");
		}
		if (p.get("data_assinatura") != null) {
			throw new IllegalArgumentException("Este contrato ja foi assinado e lancado no financeiro");
		}
		LocalDate assinatura = dataAssinatura == null ? LocalDate.now() : dataAssinatura;
		Long clienteId = ((Number) p.get("cliente_id")).longValue();
		BigDecimal total = (BigDecimal) p.get("valor_fechado");
		BigDecimal entradaTotal = p.get("entrada") == null ? BigDecimal.ZERO : (BigDecimal) p.get("entrada");
		int parcelas = p.get("parcelas") == null ? 0 : ((Number) p.get("parcelas")).intValue();
		LocalDate primeiro = p.get("primeiro_vencimento") == null ? null : ((java.sql.Date) p.get("primeiro_vencimento")).toLocalDate();
		if (total.signum() <= 0) {
			throw new IllegalArgumentException("Valor fechado zerado (pro bono/permuta): nao ha parcelas a lancar");
		}

		// areas e pesos pelo valor sugerido de cada servico
		Map<AreaAtuacao, BigDecimal> peso = new LinkedHashMap<>();
		Map<AreaAtuacao, List<String>> servicos = new LinkedHashMap<>();
		for (var i : jdbc.queryForList("select area, tipo_servico, valor_sugerido from proposta_item where proposta_id = ? order by id", id)) {
			if (i.get("area") == null) {
				throw new IllegalArgumentException("Defina a area de atuacao de todos os servicos antes de lancar o contrato");
			}
			AreaAtuacao a = AreaAtuacao.valueOf((String) i.get("area"));
			BigDecimal v = i.get("valor_sugerido") == null ? BigDecimal.ONE : ((BigDecimal) i.get("valor_sugerido")).max(BigDecimal.ONE);
			peso.merge(a, v, BigDecimal::add);
			servicos.computeIfAbsent(a, k -> new ArrayList<>()).add((String) i.get("tipo_servico"));
		}
		BigDecimal somaPesos = peso.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		List<AreaAtuacao> areas = new ArrayList<>(peso.keySet());
		BigDecimal restanteTotal = total;
		BigDecimal restanteEntrada = entradaTotal;
		for (int n = 0; n < areas.size(); n++) {
			AreaAtuacao a = areas.get(n);
			boolean ultima = n == areas.size() - 1;
			BigDecimal parte = ultima ? restanteTotal : total.multiply(peso.get(a)).divide(somaPesos, 2, RoundingMode.HALF_UP);
			BigDecimal entradaParte = ultima ? restanteEntrada : entradaTotal.multiply(peso.get(a)).divide(somaPesos, 2, RoundingMode.HALF_UP);
			restanteTotal = restanteTotal.subtract(parte);
			restanteEntrada = restanteEntrada.subtract(entradaParte);
			lancar(clienteId, id, a, String.join("; ", servicos.get(a)), parte, entradaParte, parcelas, primeiro, assinatura, (String) p.get("forma_pagamento"));
		}
		cadastros.adicionarAreas(u.id(), clienteId, EnumSet.copyOf(areas));
		jdbc.update("update proposta_honorarios set data_assinatura = ?, atualizado_em = now() where id = ?", java.sql.Date.valueOf(assinatura), id);
		auditoria.registrar(u.id(), "PROPOSTA_ASSINADA", "proposta_honorarios", String.valueOf(id), "contratos=" + areas.size());
		return montar(id, null);
	}

	private void lancar(Long clienteId, Long propostaId, AreaAtuacao area, String descricaoServicos, BigDecimal valor, BigDecimal entrada, int parcelas, LocalDate primeiro, LocalDate assinatura,
			String forma) {
		Long contrato = financeiro.criarContrato(new DadosContrato(clienteId, null, "Honorarios — " + descricaoServicos, valor, area, "MANUAL", null, forma, null, null,
				parcelas > 0 ? parcelas : null, false, assinatura));
		jdbc.update("update contrato_honorarios set proposta_id = ? where id = ?", propostaId, contrato);
		int numero = 0;
		if (entrada.signum() > 0) {
			financeiro.criarParcela(new DadosParcela(contrato, numero++, assinatura, entrada, "ABERTA", null, null, null, "ENTRADA", "MANUAL", null));
		}
		BigDecimal resto = valor.subtract(entrada);
		if (resto.signum() > 0) {
			if (parcelas > 0) {
				for (var pp : Parcelamento.dividir(resto, parcelas, primeiro)) {
					financeiro.criarParcela(new DadosParcela(contrato, ++numero, pp.vencimento(), pp.valor(), "ABERTA", null, null, null, "PARCELA " + pp.numero() + "/" + parcelas, "MANUAL", null));
				}
			} else {
				financeiro.criarParcela(new DadosParcela(contrato, ++numero, assinatura, resto, "ABERTA", null, null, null, "VALOR TOTAL", "MANUAL", null));
			}
		}
	}

	public record Resumo(Long id, String cliente, String advogado, String status, BigDecimal valorSugerido, BigDecimal valorFechado, BigDecimal valorMinimoOab, boolean abaixoDaOab,
			boolean contratoLancado, java.time.Instant criadoEm) {
	}

	/** Todas as propostas (somente socias), mais recentes primeiro. */
	@Transactional(readOnly = true)
	public List<Resumo> listar(UsuarioLogado u) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("A lista de propostas e restrita as socias");
		}
		return jdbc.query("select p.id, c.nome as cliente, a.nome as advogado, p.status, p.valor_sugerido, p.valor_fechado, p.valor_minimo_oab, "
				+ "exists (select 1 from contrato_honorarios k where k.proposta_id = p.id) as lancado, p.criado_em from proposta_honorarios p join cliente c on c.id = p.cliente_id "
				+ "join usuario a on a.id = p.advogado_id order by p.id desc limit 300", (rs, n) -> new Resumo(rs.getLong("id"), rs.getString("cliente"), rs.getString("advogado"), rs.getString("status"),
				rs.getBigDecimal("valor_sugerido"), rs.getBigDecimal("valor_fechado"), rs.getBigDecimal("valor_minimo_oab"),
				rs.getBigDecimal("valor_fechado") != null && rs.getBigDecimal("valor_fechado").compareTo(rs.getBigDecimal("valor_minimo_oab")) < 0, rs.getBoolean("lancado"),
				rs.getTimestamp("criado_em").toInstant()));
	}

	// ---- leitura montada

	private PropostaDto montar(Long id, Boolean oabVigenteConhecido) {
		var p = jdbc.queryForList("select pr.*, c.nome as cliente_nome from proposta_honorarios pr join cliente c on c.id = pr.cliente_id where pr.id = ?", id).get(0);
		List<ItemProposta> itens = jdbc.query("select * from proposta_item where proposta_id = ? order by id", (rs, n) -> new ItemProposta(rs.getLong("id"), rs.getString("chave_servico"),
				rs.getString("area"), rs.getString("tipo_servico"), rs.getString("fase"), rs.getBigDecimal("minimo_oab"), rs.getBigDecimal("valor_tabela"), rs.getBigDecimal("valor_sugerido"),
				rs.getString("exito_texto")), id);
		List<Servico> servicos = jdbc.query("select * from proposta_item where proposta_id = ? order by id", (rs, n) -> new Servico(rs.getString("chave_servico"), rs.getString("area"),
				rs.getString("tipo_servico"), rs.getString("fase"), null, null, rs.getBigDecimal("minimo_oab"), rs.getBigDecimal("valor_tabela"), rs.getBigDecimal("valor_sugerido"),
				rs.getString("exito_texto"), rs.getBigDecimal("exito_min"), rs.getBigDecimal("exito_max")), id);
		var quadro = servicos.isEmpty() ? null : SugestaoHonorarios.calcular(servicos, (BigDecimal) p.get("desconto_pacote"), null);
		BigDecimal minimo = (BigDecimal) p.get("valor_minimo_oab");
		BigDecimal fechado = (BigDecimal) p.get("valor_fechado");
		boolean abaixo = fechado != null && fechado.compareTo(minimo) < 0;
		boolean oab = oabVigenteConhecido != null ? oabVigenteConhecido
				: !jdbc.queryForList("select 1 from tabela_honorarios_versao where tabela = 'OAB' and seccional = ? and vigente", p.get("seccional")).isEmpty();
		return new PropostaDto(id, ((Number) p.get("cliente_id")).longValue(), (String) p.get("cliente_nome"), p.get("agendamento_id") == null ? null : ((Number) p.get("agendamento_id")).longValue(),
				((Number) p.get("advogado_id")).longValue(), (String) p.get("seccional"), (String) p.get("status"), minimo, (BigDecimal) p.get("valor_sugerido"), (BigDecimal) p.get("desconto_pacote"),
				fechado, (String) p.get("justificativa_tipo"), (String) p.get("justificativa"), (BigDecimal) p.get("entrada"), p.get("parcelas") == null ? null : ((Number) p.get("parcelas")).intValue(),
				p.get("primeiro_vencimento") == null ? null : ((java.sql.Date) p.get("primeiro_vencimento")).toLocalDate(), (String) p.get("forma_pagamento"),
				p.get("data_assinatura") == null ? null : ((java.sql.Date) p.get("data_assinatura")).toLocalDate(),
				!jdbc.queryForList("select 1 from contrato_honorarios where proposta_id = ?", id).isEmpty(), ((java.sql.Timestamp) p.get("criado_em")).toInstant(),
				p.get("fechado_em") == null ? null : ((java.sql.Timestamp) p.get("fechado_em")).toInstant(), itens, quadro, abaixo,
				abaixo ? minimo.subtract(fechado).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO, oab);
	}
}
