package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.honorarios.SugestaoHonorarios.Quadro;
import br.com.crispimcampos.gestao.honorarios.SugestaoHonorarios.Servico;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o quadro de sugestao com as tabelas vigentes: minimo da OAB da seccional do local do servico + valor da tabela do
 * escritorio. Uso interno do modulo; quem expoe decide a permissao (socias e advogado do atendimento).
 */
@Service
public class SugestaoService {

	public record Resultado(Quadro quadro, Seccional seccional, boolean tabelaOabVigente, List<Servico> servicos) {
	}

	private final JdbcTemplate jdbc;

	SugestaoService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public Resultado calcular(Seccional seccional, List<String> chaves) {
		if (chaves == null || chaves.isEmpty()) {
			throw new IllegalArgumentException("Escolha ao menos um servico");
		}
		boolean oabVigente = !jdbc.queryForList("select 1 from tabela_honorarios_versao where tabela = 'OAB' and seccional = ? and vigente", seccional.name()).isEmpty();
		var politica = jdbc.queryForList("select fator_escritorio, desconto_pacote from tabela_honorarios_versao where tabela = 'ESCRITORIO' and vigente");
		BigDecimal fator = politica.isEmpty() ? null : (BigDecimal) politica.get(0).get("fator_escritorio");
		BigDecimal pacote = politica.isEmpty() ? BigDecimal.ZERO : (BigDecimal) politica.get(0).get("desconto_pacote");

		String marcas = String.join(",", chaves.stream().map(c -> "?").toList());
		List<Object> args = new ArrayList<>(chaves);
		args.add(seccional.name());
		Map<String, Map<String, Object>> oab = new LinkedHashMap<>();
		Map<String, Map<String, Object>> esc = new LinkedHashMap<>();
		jdbc.query("select i.id, i.chave_servico, i.area, i.tipo_servico, i.fase, i.valor, i.valor_sugerido, i.exito_texto, i.exito_min, i.exito_max, v.tabela from tabela_honorarios_item i "
				+ "join tabela_honorarios_versao v on v.id = i.versao_id where v.vigente and i.ativo and i.chave_servico in (" + marcas + ") and (v.tabela = 'ESCRITORIO' or v.seccional = ?)", rs -> {
					Map<String, Object> m = new LinkedHashMap<>();
					for (String c : List.of("id", "area", "tipo_servico", "fase", "valor", "valor_sugerido", "exito_texto", "exito_min", "exito_max")) {
						m.put(c, rs.getObject(c));
					}
					(rs.getString("tabela").equals("OAB") ? oab : esc).put(rs.getString("chave_servico"), m);
				}, args.toArray());

		List<Servico> servicos = new ArrayList<>();
		for (String chave : chaves) {
			Map<String, Object> o = oab.get(chave);
			Map<String, Object> e = esc.get(chave);
			Map<String, Object> base = e != null ? e : o;
			if (base == null) {
				throw new IllegalArgumentException("Servico nao encontrado nas tabelas vigentes");
			}
			Map<String, Object> exito = e != null && e.get("exito_texto") != null ? e : o;
			servicos.add(new Servico(chave, (String) base.get("area"), (String) base.get("tipo_servico"), (String) base.get("fase"), o == null ? null : ((Number) o.get("id")).longValue(),
					e == null ? null : ((Number) e.get("id")).longValue(), o == null ? null : (BigDecimal) o.get("valor"), e == null ? null : (BigDecimal) e.get("valor"),
					e == null ? null : (BigDecimal) e.get("valor_sugerido"), exito == null ? null : (String) exito.get("exito_texto"), exito == null ? null : (BigDecimal) exito.get("exito_min"),
					exito == null ? null : (BigDecimal) exito.get("exito_max")));
		}
		return new Resultado(SugestaoHonorarios.calcular(servicos, pacote, fator), seccional, oabVigente, servicos);
	}
}
