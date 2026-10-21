package br.com.crispimcampos.gestao.importacao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** {@link Existentes} sobre o banco. Clientes sao carregados uma vez (escritorio pequeno) para casar por nome normalizado. */
final class ExistentesJdbc implements Existentes {

	private final JdbcTemplate jdbc;
	private final Map<String, Map<String, Object>> clientesPorNome = new HashMap<>();
	private final Map<String, Map<String, Object>> clientesPorDocumento = new HashMap<>();

	ExistentesJdbc(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		Map<Long, List<String>> areas = new HashMap<>();
		jdbc.query("select cliente_id, area from cliente_area order by area",
				rs -> {
					areas.computeIfAbsent(rs.getLong("cliente_id"), k -> new ArrayList<>()).add(rs.getString("area"));
				});
		jdbc.query("select id, nome, documento, rg, estado_civil, endereco, telefone, email, ativo from cliente order by ativo desc, id", rs -> {
			Map<String, Object> m = new LinkedHashMap<>();
			long id = rs.getLong("id");
			m.put("id", id);
			m.put("nome", rs.getString("nome"));
			m.put("documento", rs.getString("documento"));
			m.put("rg", rs.getString("rg"));
			m.put("estadoCivil", rs.getString("estado_civil"));
			m.put("endereco", rs.getString("endereco"));
			m.put("telefone", rs.getString("telefone"));
			m.put("email", rs.getString("email"));
			m.put("areas", areas.getOrDefault(id, List.of()));
			clientesPorNome.putIfAbsent(Texto.normalizar(rs.getString("nome")), m);
			if (rs.getString("documento") != null) {
				clientesPorDocumento.putIfAbsent(rs.getString("documento"), m);
			}
		});
	}

	@Override
	public Optional<Map<String, Object>> cliente(String documento, String nomeNormalizado) {
		if (documento != null && clientesPorDocumento.containsKey(documento)) {
			return Optional.of(clientesPorDocumento.get(documento));
		}
		return Optional.ofNullable(clientesPorNome.get(nomeNormalizado));
	}

	@Override
	public Optional<Map<String, Object>> contrato(String chave) {
		return jdbc.queryForList("select id, cliente_id, valor_total as \"valorTotal\", percentual_exito as \"percentualExito\", forma_pagamento as \"formaPagamento\", "
				+ "situacao_origem as \"situacaoOrigem\", area from contrato_honorarios where chave_origem = ?", chave).stream().findFirst()
				.map(ExistentesJdbc::copia);
	}

	@Override
	public java.util.List<Map<String, Object>> contratosDoCliente(Long clienteId) {
		return jdbc.queryForList("select c.id, c.chave_origem as chave, c.valor_total as \"valorTotal\", p.numero as cnj, c.data_assinatura as \"dataAssinatura\", "
				+ "c.forma_pagamento as \"formaPagamento\", c.numero_parcelas as \"numeroParcelas\" from contrato_honorarios c left join processo p on p.id = c.processo_id "
				+ "where c.cliente_id = ? and c.ativo order by c.id", clienteId).stream().map(m -> {
					Map<String, Object> r = copia(m);
					if (m.get("dataAssinatura") != null) {
						r.put("dataAssinatura", String.valueOf(m.get("dataAssinatura")));
					}
					return r;
				}).toList();
	}

	@Override
	public Optional<Map<String, Object>> documentoPorDrive(String driveId) {
		return jdbc.query("select id, drive_modificado_em from documento_cliente where drive_id = ?", (rs, n) -> {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("id", rs.getLong("id"));
			r.put("driveModificadoEm", rs.getTimestamp("drive_modificado_em") == null ? null : rs.getTimestamp("drive_modificado_em").toInstant().toString());
			return r;
		}, driveId).stream().findFirst();
	}

	@Override
	public Optional<Map<String, Object>> parcela(String chave) {
		return jdbc.queryForList("select id, contrato_id, valor, vencimento, situacao from parcela where chave_origem = ?", chave).stream().findFirst()
				.map(m -> {
					Map<String, Object> c = copia(m);
					c.put("vencimento", String.valueOf(m.get("vencimento")));
					return c;
				});
	}

	@Override
	public Optional<Map<String, Object>> processo(String cnj) {
		return jdbc.queryForList("select p.id, p.cliente_id, c.nome from processo p join cliente c on c.id = p.cliente_id where p.numero = ?", cnj).stream()
				.findFirst().map(m -> {
					Map<String, Object> c = copia(m);
					c.put("clienteNorm", Texto.normalizar(String.valueOf(m.get("nome"))));
					return c;
				});
	}

	@Override
	public Optional<Map<String, Object>> despesaEscritorio(String chave) {
		return jdbc.queryForList("select id from despesa_escritorio where chave_origem = ?", chave).stream().findFirst().map(ExistentesJdbc::copia);
	}

	private static Map<String, Object> copia(Map<String, Object> m) {
		return new LinkedHashMap<>(m);
	}
}
