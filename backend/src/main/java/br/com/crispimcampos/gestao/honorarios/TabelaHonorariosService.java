package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.honorarios.PlanilhaHonorarios.Servico;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.DadosItem;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.ItemDto;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.ResultadoImportacao;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.ServicoCatalogo;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.VersaoDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tabelas de honorarios: a da OAB (por seccional) e a do escritorio, separadas, versionadas e editaveis. Somente as socias leem
 * valores e editam (invariante 1); advogados so enxergam o catalogo de servicos, sem valores.
 */
@Service
public class TabelaHonorariosService {

	private final JdbcTemplate jdbc;
	private final AuditoriaService auditoria;

	TabelaHonorariosService(JdbcTemplate jdbc, AuditoriaService auditoria) {
		this.jdbc = jdbc;
		this.auditoria = auditoria;
	}

	static void exigirSocia(UsuarioLogado u) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("As tabelas de honorarios sao restritas as socias");
		}
	}

	// ---- leitura

	@Transactional(readOnly = true)
	public List<VersaoDto> versoes(UsuarioLogado ator) {
		exigirSocia(ator);
		return jdbc.query("select v.*, (select count(*) from tabela_honorarios_item i where i.versao_id = v.id and i.ativo) as itens from tabela_honorarios_versao v "
				+ "order by v.tabela, v.seccional nulls last, v.id desc", (rs, n) -> new VersaoDto(rs.getLong("id"), rs.getString("tabela"), rs.getString("seccional"),
				rs.getString("referencia"), rs.getBigDecimal("valor_urh"), rs.getBigDecimal("fator_escritorio"), rs.getBigDecimal("desconto_padrao"),
				rs.getBigDecimal("desconto_trabalhista"), rs.getBigDecimal("desconto_pacote"), rs.getBoolean("vigente"), rs.getString("arquivo_nome"),
				rs.getTimestamp("criado_em").toInstant(), rs.getTimestamp("substituida_em") == null ? null : rs.getTimestamp("substituida_em").toInstant(),
				rs.getLong("itens")));
	}

	@Transactional(readOnly = true)
	public List<ItemDto> itens(UsuarioLogado ator, Long versaoId, boolean incluirExcluidos) {
		exigirSocia(ator);
		return jdbc.query("select i.*, v.tabela from tabela_honorarios_item i join tabela_honorarios_versao v on v.id = i.versao_id where i.versao_id = ? "
				+ (incluirExcluidos ? "" : "and i.ativo ") + "order by i.ordem, i.id", (rs, n) -> item(rs), versaoId);
	}

	/** Servicos das tabelas vigentes sem nenhum valor: base para o advogado escolher o servico no atendimento. */
	@Transactional(readOnly = true)
	public List<ServicoCatalogo> catalogo(UsuarioLogado ator) {
		if (ator.perfil() != Perfil.SOCIA && ator.perfil() != Perfil.ADVOGADO) {
			throw new SecurityException("Sem acesso ao catalogo de servicos");
		}
		return jdbc.query("select distinct on (i.chave_servico) i.chave_servico, i.area, i.tipo_servico, i.fase from tabela_honorarios_item i "
				+ "join tabela_honorarios_versao v on v.id = i.versao_id where v.vigente and i.ativo order by i.chave_servico, v.tabela desc",
				(rs, n) -> new ServicoCatalogo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))).stream()
				.sorted(java.util.Comparator.comparing((ServicoCatalogo s) -> String.valueOf(s.area())).thenComparing(ServicoCatalogo::tipoServico)).toList();
	}

	// ---- edicao

	@Transactional
	public ItemDto criarItem(UsuarioLogado ator, Long versaoId, DadosItem d) {
		exigirSocia(ator);
		validar(d);
		Long id = jdbc.queryForObject("insert into tabela_honorarios_item (versao_id, area, tipo_servico, fase, chave_servico, item_oab, valor_urh, valor, derivado, "
				+ "desconto_percentual, valor_sugerido, exito_texto, exito_min, exito_max, parcelamento, observacoes, ordem) values (?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?, ?, ?, ?, ?, ?, "
				+ "(select coalesce(max(ordem), 0) + 1 from tabela_honorarios_item where versao_id = ?)) returning id", Long.class, versaoId, d.area(), d.tipoServico().trim(),
				d.fase(), ChaveServico.de(d.area(), d.tipoServico(), d.fase()), d.itemOab(), d.valorUrh(), d.valor(), d.descontoPercentual(), sugerido(d), d.exitoTexto(),
				d.exitoMin(), d.exitoMax(), d.parcelamento(), d.observacoes(), versaoId);
		auditoria.registrar(ator.id(), "HONORARIOS_ITEM_CRIADO", "tabela_honorarios_item", String.valueOf(id), null);
		return item(id);
	}

	@Transactional
	public ItemDto atualizarItem(UsuarioLogado ator, Long itemId, DadosItem d) {
		exigirSocia(ator);
		validar(d);
		int n = jdbc.update("update tabela_honorarios_item set area = ?, tipo_servico = ?, fase = ?, chave_servico = ?, item_oab = ?, valor_urh = ?, valor = ?, derivado = false, "
				+ "desconto_percentual = ?, valor_sugerido = ?, exito_texto = ?, exito_min = ?, exito_max = ?, parcelamento = ?, observacoes = ? where id = ?", d.area(),
				d.tipoServico().trim(), d.fase(), ChaveServico.de(d.area(), d.tipoServico(), d.fase()), d.itemOab(), d.valorUrh(), d.valor(), d.descontoPercentual(), sugerido(d),
				d.exitoTexto(), d.exitoMin(), d.exitoMax(), d.parcelamento(), d.observacoes(), itemId);
		if (n == 0) {
			throw new IllegalArgumentException("Item nao encontrado");
		}
		auditoria.registrar(ator.id(), "HONORARIOS_ITEM_ATUALIZADO", "tabela_honorarios_item", String.valueOf(itemId), null);
		return item(itemId);
	}

	/** Exclusao logica: o item sai das sugestoes, mas fica na versao (historico). */
	@Transactional
	public void excluirItem(UsuarioLogado ator, Long itemId) {
		exigirSocia(ator);
		if (jdbc.update("update tabela_honorarios_item set ativo = false where id = ?", itemId) == 0) {
			throw new IllegalArgumentException("Item nao encontrado");
		}
		auditoria.registrar(ator.id(), "HONORARIOS_ITEM_EXCLUIDO", "tabela_honorarios_item", String.valueOf(itemId), null);
	}

	@Transactional
	public void restaurarItem(UsuarioLogado ator, Long itemId) {
		exigirSocia(ator);
		jdbc.update("update tabela_honorarios_item set ativo = true where id = ?", itemId);
		auditoria.registrar(ator.id(), "HONORARIOS_ITEM_RESTAURADO", "tabela_honorarios_item", String.valueOf(itemId), null);
	}

	/**
	 * Atualiza o valor da URH da tabela da OAB: recalcula o minimo em R$ dos itens que tem valor em URH e, na tabela do escritorio,
	 * os valores derivados (minimo x fator, com o desconto de cada item, nunca abaixo do minimo).
	 */
	@Transactional
	public void atualizarUrh(UsuarioLogado ator, Long versaoOabId, BigDecimal novoValor) {
		exigirSocia(ator);
		if (novoValor == null || novoValor.signum() <= 0) {
			throw new IllegalArgumentException("Informe o valor da URH");
		}
		var v = jdbc.queryForList("select seccional, vigente from tabela_honorarios_versao where id = ? and tabela = 'OAB'", versaoOabId);
		if (v.isEmpty()) {
			throw new IllegalArgumentException("Versao da tabela da OAB nao encontrada");
		}
		jdbc.update("update tabela_honorarios_versao set valor_urh = ? where id = ?", novoValor, versaoOabId);
		jdbc.update("update tabela_honorarios_item set valor = round(valor_urh * ?, 2) where versao_id = ? and valor_urh is not null", novoValor, versaoOabId);
		if (Boolean.TRUE.equals(v.get(0).get("vigente"))) {
			recalcularDerivados(String.valueOf(v.get(0).get("seccional")));
		}
		auditoria.registrar(ator.id(), "HONORARIOS_URH_ATUALIZADA", "tabela_honorarios_versao", String.valueOf(versaoOabId), "urh=" + novoValor.toPlainString());
	}

	/** Politica comercial do escritorio (fator, descontos e desconto por pacote); recalcula os valores derivados. */
	@Transactional
	public void atualizarPolitica(UsuarioLogado ator, Long versaoEscritorioId, BigDecimal fator, BigDecimal descontoPadrao, BigDecimal descontoTrabalhista, BigDecimal descontoPacote) {
		exigirSocia(ator);
		for (BigDecimal d : new BigDecimal[] {descontoPadrao, descontoTrabalhista, descontoPacote}) {
			if (d != null && (d.signum() < 0 || d.compareTo(BigDecimal.ONE) > 0)) {
				throw new IllegalArgumentException("Desconto deve estar entre 0 e 1 (ex.: 0,10 = 10%)");
			}
		}
		if (fator != null && fator.compareTo(BigDecimal.ONE) < 0) {
			throw new IllegalArgumentException("O fator do escritorio nao pode ser menor que 1 (abaixo do minimo da OAB)");
		}
		int n = jdbc.update("update tabela_honorarios_versao set fator_escritorio = coalesce(?, fator_escritorio), desconto_padrao = coalesce(?, desconto_padrao), "
				+ "desconto_trabalhista = coalesce(?, desconto_trabalhista), desconto_pacote = coalesce(?, desconto_pacote) where id = ? and tabela = 'ESCRITORIO'", fator,
				descontoPadrao, descontoTrabalhista, descontoPacote, versaoEscritorioId);
		if (n == 0) {
			throw new IllegalArgumentException("Versao da tabela do escritorio nao encontrada");
		}
		recalcularDerivados("DF");
		auditoria.registrar(ator.id(), "HONORARIOS_POLITICA_ATUALIZADA", "tabela_honorarios_versao", String.valueOf(versaoEscritorioId), null);
	}

	// ---- importacao

	/**
	 * Importa a planilha: cria uma NOVA versao da tabela da OAB da seccional (a anterior vira historico) e, se pedido, uma nova
	 * versao da tabela do escritorio. Itens editados a mao na versao antiga permanecem la, consultaveis.
	 */
	@Transactional
	public ResultadoImportacao importar(UsuarioLogado ator, String nomeArquivo, byte[] arquivo, Seccional seccional, boolean substituirEscritorio) {
		exigirSocia(ator);
		var dados = PlanilhaHonorarios.ler(arquivo);
		var p = dados.parametros();
		jdbc.update("update tabela_honorarios_versao set vigente = false, substituida_em = now() where tabela = 'OAB' and seccional = ? and vigente", seccional.name());
		Long oab = jdbc.queryForObject("insert into tabela_honorarios_versao (tabela, seccional, referencia, valor_urh, arquivo_nome, criado_por) values ('OAB', ?, ?, ?, ?, ?) returning id",
				Long.class, seccional.name(), p.referencia(), p.valorUrh(), nomeArquivo, ator.id());
		Long esc = null;
		if (substituirEscritorio) {
			BigDecimal pacoteAnterior = jdbc.queryForList("select desconto_pacote from tabela_honorarios_versao where tabela = 'ESCRITORIO' and vigente", BigDecimal.class).stream()
					.findFirst().orElse(BigDecimal.ZERO);
			jdbc.update("update tabela_honorarios_versao set vigente = false, substituida_em = now() where tabela = 'ESCRITORIO' and vigente");
			esc = jdbc.queryForObject("insert into tabela_honorarios_versao (tabela, referencia, fator_escritorio, desconto_padrao, desconto_trabalhista, desconto_pacote, arquivo_nome, "
					+ "criado_por) values ('ESCRITORIO', ?, ?, ?, ?, ?, ?, ?) returning id", Long.class, p.referencia(), p.fator(), p.descontoPadrao(), p.descontoTrabalhista(),
					pacoteAnterior, nomeArquivo, ator.id());
		}
		Set<String> usadas = new HashSet<>();
		int semArea = 0;
		int semValor = 0;
		int ordem = 0;
		List<String> avisos = new ArrayList<>(dados.avisos());
		for (Servico s : dados.servicos()) {
			ordem++;
			String area = s.area() == null ? null : s.area().name();
			String tipo = s.tipo();
			String chave = ChaveServico.de(area, tipo, s.fase());
			if (!usadas.add(chave)) {
				tipo = tipo + " (" + s.linha() + ")";
				chave = ChaveServico.de(area, tipo, s.fase());
				usadas.add(chave);
				avisos.add("Servico repetido na linha " + s.linha() + ": mantido com o numero da linha no nome");
			}
			if (area == null) {
				semArea++;
				avisos.add("Linha " + s.linha() + ": " + s.avisos().get(0));
			}
			if (s.minimoOab() == null) {
				semValor++;
			}
			jdbc.update("insert into tabela_honorarios_item (versao_id, area, tipo_servico, fase, chave_servico, item_oab, valor_urh, valor, exito_texto, exito_min, exito_max, observacoes, ordem) "
					+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", oab, area, tipo, s.fase(), chave, s.itemOab(), s.urh(), s.minimoOab(), s.exitoTexto(), s.exitoMin(), s.exitoMax(),
					limitar(s.observacoes(), 500), ordem);
			if (esc != null) {
				jdbc.update("insert into tabela_honorarios_item (versao_id, area, tipo_servico, fase, chave_servico, item_oab, valor, derivado, desconto_percentual, valor_sugerido, "
						+ "exito_texto, exito_min, exito_max, parcelamento, observacoes, ordem) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", esc, area, tipo, s.fase(), chave,
						s.itemOab(), s.valorTabela(), s.derivado(), s.desconto(), s.valorSugerido(), s.exitoTexto(), s.exitoMin(), s.exitoMax(), limitar(s.parcelamento(), 400),
						limitar(s.observacoes(), 500), ordem);
			}
		}
		auditoria.registrar(ator.id(), "HONORARIOS_IMPORTADOS", "tabela_honorarios_versao", String.valueOf(oab),
				"seccional=" + seccional + " servicos=" + dados.servicos().size() + " escritorio=" + substituirEscritorio);
		return new ResultadoImportacao(oab, esc, dados.servicos().size(), semArea, semValor, avisos);
	}

	// ---- internos

	private void recalcularDerivados(String seccional) {
		var oab = jdbc.queryForList("select id from tabela_honorarios_versao where tabela = 'OAB' and seccional = ? and vigente", Long.class, seccional);
		var esc = jdbc.queryForList("select id, fator_escritorio from tabela_honorarios_versao where tabela = 'ESCRITORIO' and vigente");
		if (oab.isEmpty() || esc.isEmpty() || esc.get(0).get("fator_escritorio") == null) {
			return;
		}
		BigDecimal fator = (BigDecimal) esc.get(0).get("fator_escritorio");
		Long escId = ((Number) esc.get(0).get("id")).longValue();
		for (var e : jdbc.queryForList("select e.id, e.desconto_percentual, o.valor as minimo from tabela_honorarios_item e join tabela_honorarios_item o on o.chave_servico = e.chave_servico "
				+ "and o.versao_id = ? and o.ativo where e.versao_id = ? and e.derivado and e.ativo and o.valor is not null", oab.get(0), escId)) {
			BigDecimal minimo = (BigDecimal) e.get("minimo");
			BigDecimal tabela = minimo.multiply(fator).setScale(2, RoundingMode.HALF_UP);
			BigDecimal desc = e.get("desconto_percentual") == null ? BigDecimal.ZERO : (BigDecimal) e.get("desconto_percentual");
			BigDecimal sugerido = tabela.multiply(BigDecimal.ONE.subtract(desc)).setScale(2, RoundingMode.HALF_UP).max(minimo);
			jdbc.update("update tabela_honorarios_item set valor = ?, valor_sugerido = ? where id = ?", tabela, sugerido, ((Number) e.get("id")).longValue());
		}
	}

	private static BigDecimal sugerido(DadosItem d) {
		if (d.valorSugerido() != null) {
			return d.valorSugerido();
		}
		if (d.valor() != null && d.descontoPercentual() != null) {
			return d.valor().multiply(BigDecimal.ONE.subtract(d.descontoPercentual())).setScale(2, RoundingMode.HALF_UP);
		}
		return d.valor();
	}

	private static void validar(DadosItem d) {
		if (d.tipoServico() == null || d.tipoServico().isBlank()) {
			throw new IllegalArgumentException("Informe o tipo de servico");
		}
		if (d.valor() != null && d.valor().signum() < 0) {
			throw new IllegalArgumentException("O valor nao pode ser negativo");
		}
		if (d.area() != null) {
			AreaAtuacao.valueOf(d.area());
		}
		if (d.fase() != null && !java.util.List.of("JUDICIAL", "EXTRAJUDICIAL", "CONHECIMENTO", "RECURSO", "CUMPRIMENTO").contains(d.fase())) {
			throw new IllegalArgumentException("Fase invalida");
		}
		if (d.descontoPercentual() != null && (d.descontoPercentual().signum() < 0 || d.descontoPercentual().compareTo(BigDecimal.ONE) > 0)) {
			throw new IllegalArgumentException("Desconto deve estar entre 0 e 1");
		}
	}

	private ItemDto item(Long id) {
		return jdbc.query("select i.*, v.tabela from tabela_honorarios_item i join tabela_honorarios_versao v on v.id = i.versao_id where i.id = ?", (rs, n) -> item(rs), id).get(0);
	}

	private static ItemDto item(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new ItemDto(rs.getLong("id"), rs.getLong("versao_id"), rs.getString("tabela"), rs.getString("area"), rs.getString("tipo_servico"), rs.getString("fase"),
				rs.getString("chave_servico"), rs.getString("item_oab"), rs.getBigDecimal("valor_urh"), rs.getBigDecimal("valor"), rs.getBoolean("derivado"),
				rs.getBigDecimal("desconto_percentual"), rs.getBigDecimal("valor_sugerido"), rs.getString("exito_texto"), rs.getBigDecimal("exito_min"), rs.getBigDecimal("exito_max"),
				rs.getString("parcelamento"), rs.getString("observacoes"), rs.getBoolean("ativo"));
	}

	private static String limitar(String s, int max) {
		return s == null ? null : s.length() > max ? s.substring(0, max) : s;
	}
}
