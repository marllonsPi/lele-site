package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.importacao.ItemPrevia.Acao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Decisao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Resultado;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Tipo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Persistencia (JDBC) das importacoes e dos itens da previa. JSON em colunas TEXT. */
@Repository
class ImportacaoRepositorio {

	record Cabecalho(Long id, String origem, String modo, String arquivoNome, String status, Long responsavelPadraoId, Long criadoPor,
			OffsetDateTime criadoEm, Long confirmadoPor, OffsetDateTime confirmadoEm, String relatorio) {
	}

	private static final TypeReference<List<String>> LISTA = new TypeReference<>() {
	};
	private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
	};

	private final JdbcTemplate jdbc;
	private final JsonMapper json;

	ImportacaoRepositorio(JdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Transactional
	Long criar(String origem, String modo, String arquivo, Long responsavelPadraoId, Long criadoPor) {
		return jdbc.queryForObject("insert into importacao (origem, modo, arquivo_nome, responsavel_padrao_id, criado_por) values (?, ?, ?, ?, ?) returning id",
				Long.class, origem, modo, arquivo, responsavelPadraoId, criadoPor);
	}

	@Transactional
	void salvarItens(Long importacaoId, List<ItemPrevia> itens) {
		jdbc.batchUpdate("insert into importacao_item (importacao_id, ordem, tipo, chave, rotulo, acao, divergencias, importado, existente, decisao, ajustes) "
				+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", itens, 200, (ps, i) -> {
					ps.setLong(1, importacaoId);
					ps.setInt(2, i.ordem());
					ps.setString(3, i.tipo().name());
					ps.setString(4, limitar(i.chave(), 250));
					ps.setString(5, limitar(i.rotulo(), 300));
					ps.setString(6, i.acao().name());
					ps.setString(7, escrever(i.divergencias()));
					ps.setString(8, escrever(i.importado()));
					ps.setString(9, i.existente() == null ? null : escrever(i.existente()));
					ps.setString(10, i.decisao() == null ? null : i.decisao().name());
					ps.setString(11, escrever(i.ajustes()));
				});
	}

	Optional<Cabecalho> cabecalho(Long id) {
		return jdbc.query("select * from importacao where id = ?", (rs, n) -> new Cabecalho(rs.getLong("id"), rs.getString("origem"), rs.getString("modo"),
				rs.getString("arquivo_nome"), rs.getString("status"), (Long) rs.getObject("responsavel_padrao_id"), (Long) rs.getObject("criado_por"),
				rs.getObject("criado_em", OffsetDateTime.class), (Long) rs.getObject("confirmado_por"),
				rs.getObject("confirmado_em", OffsetDateTime.class), rs.getString("relatorio")), id).stream().findFirst();
	}

	List<Cabecalho> listar() {
		return jdbc.query("select * from importacao order by id desc limit 100", (rs, n) -> new Cabecalho(rs.getLong("id"), rs.getString("origem"),
				rs.getString("modo"), rs.getString("arquivo_nome"), rs.getString("status"), (Long) rs.getObject("responsavel_padrao_id"),
				(Long) rs.getObject("criado_por"), rs.getObject("criado_em", OffsetDateTime.class), (Long) rs.getObject("confirmado_por"),
				rs.getObject("confirmado_em", OffsetDateTime.class), null));
	}

	List<ItemPrevia> itens(Long importacaoId) {
		return jdbc.query("select * from importacao_item where importacao_id = ? order by ordem", (rs, n) -> item(rs), importacaoId);
	}

	Optional<ItemPrevia> item(Long importacaoId, Long itemId) {
		return jdbc.query("select * from importacao_item where importacao_id = ? and id = ?", (rs, n) -> item(rs), importacaoId, itemId).stream().findFirst();
	}

	private ItemPrevia item(java.sql.ResultSet rs) throws java.sql.SQLException {
		String dec = rs.getString("decisao");
		String res = rs.getString("resultado");
		String ex = rs.getString("existente");
		return new ItemPrevia(rs.getLong("id"), rs.getInt("ordem"), Tipo.valueOf(rs.getString("tipo")), rs.getString("chave"), rs.getString("rotulo"),
				Acao.valueOf(rs.getString("acao")), ler(rs.getString("divergencias"), LISTA), ler(rs.getString("importado"), MAPA),
				ex == null ? null : ler(ex, MAPA), dec == null ? null : Decisao.valueOf(dec), ler(rs.getString("ajustes"), MAPA),
				res == null ? null : Resultado.valueOf(res), rs.getString("motivo"));
	}

	@Transactional
	void decidir(Long itemId, Decisao decisao, Map<String, Object> ajustes) {
		jdbc.update("update importacao_item set decisao = ?, ajustes = ? where id = ?", decisao == null ? null : decisao.name(), escrever(ajustes), itemId);
	}

	@Transactional
	void decidirTodos(Long importacaoId, Acao acao, Tipo tipo, Decisao decisao) {
		jdbc.update("update importacao_item set decisao = ? where importacao_id = ? and acao = ? and (?::varchar is null or tipo = ?)", decisao.name(),
				importacaoId, acao.name(), tipo == null ? null : tipo.name(), tipo == null ? null : tipo.name());
	}

	@Transactional
	void marcarResultado(Long itemId, Resultado resultado, String motivo) {
		jdbc.update("update importacao_item set resultado = ?, motivo = ?, aplicado_em = now() where id = ?", resultado.name(), limitar(motivo, 1000), itemId);
	}

	@Transactional
	void concluir(Long id, Long confirmadoPor, String relatorioJson) {
		jdbc.update("update importacao set status = 'CONCLUIDA', confirmado_por = ?, confirmado_em = now(), relatorio = ? where id = ?", confirmadoPor, relatorioJson, id);
	}

	@Transactional
	void cancelar(Long id, Long por) {
		jdbc.update("update importacao set status = 'CANCELADA', confirmado_por = ?, confirmado_em = now() where id = ? and status = 'PREVIA'", por, id);
	}

	/** Conflito igual (mesma chave e mesmos valores importados) que uma socia ja decidiu manter: nao precisa voltar a incomodar. */
	boolean conflitoJaMantido(ItemPrevia item) {
		// compara o texto gravado no banco (nao uma re-serializacao): mesma fonte, mesmos valores
		Integer n = jdbc.queryForObject("select count(*) from importacao_item i join importacao m on m.id = i.importacao_id, importacao_item atual "
				+ "where atual.id = ? and i.id <> atual.id and i.chave = atual.chave and i.importado = atual.importado "
				+ "and i.resultado in ('IGNORADO', 'APLICADO') and i.motivo like 'Conflito:%' and m.status = 'CONCLUIDA'", Integer.class, item.id());
		return n != null && n > 0;
	}

	String escrever(Object o) {
		return json.writeValueAsString(o);
	}

	private <T> T ler(String s, TypeReference<T> tipo) {
		return json.readValue(s, tipo);
	}

	private static String limitar(String s, int max) {
		return s == null ? null : s.length() > max ? s.substring(0, max) : s;
	}
}
