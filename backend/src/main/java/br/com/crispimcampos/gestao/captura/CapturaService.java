package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.OabDto;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Execucao da captura (item 3): para cada OAB ativa e cada fonte, busca, filtra, cruza e enfileira. A falha de uma
 * fonte (ou OAB) e registrada e nao derruba as demais. Cada passada e idempotente, entao a janela pode se sobrepor.
 */
@Service
public class CapturaService {

	private static final Logger log = LoggerFactory.getLogger(CapturaService.class);

	private final List<FonteDePublicacoes> fontes;
	private final PipelineCaptura pipeline;
	private final UsuarioService usuarios;
	private final AuditoriaService auditoria;
	private final JdbcTemplate jdbc;

	CapturaService(List<FonteDePublicacoes> fontes, PipelineCaptura pipeline, UsuarioService usuarios,
			AuditoriaService auditoria, JdbcTemplate jdbc) {
		this.fontes = fontes;
		this.pipeline = pipeline;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.jdbc = jdbc;
	}

	public record Execucao(Long id, String fonte, java.time.Instant inicio, java.time.Instant fim, String resultado,
			int itensLidos, int itensNovos, String erro) {
	}

	/** OABs ativas de usuarios ativos (socias e advogados). */
	public List<OabMonitorada> monitoradas() {
		List<OabMonitorada> lista = new ArrayList<>();
		for (UsuarioDto u : usuarios.listar()) {
			if (!u.ativo()) {
				continue;
			}
			for (OabDto o : u.oabs()) {
				if (o.ativa()) {
					lista.add(new OabMonitorada(u.id(), o.numero(), o.uf()));
				}
			}
		}
		return lista;
	}

	public List<Execucao> executar(Long atorId, LocalDate de, LocalDate ate) {
		List<Execucao> resultado = new ArrayList<>();
		List<OabMonitorada> oabs = monitoradas();
		for (FonteDePublicacoes fonte : fontes) {
			Long id = jdbc.queryForObject("insert into execucao_captura (fonte) values (?) returning id", Long.class, fonte.fonte().name());
			int lidos = 0;
			int novos = 0;
			List<String> erros = new ArrayList<>();
			List<Long> proprios = fonte.titularesProprios();
			// fonte propria: um "monitorado" sem OAB por titular conectado; senao, um por OAB ativa
			List<OabMonitorada> alvos = proprios == null ? oabs
					: proprios.stream().map(t -> new OabMonitorada(t, null, null)).toList();
			for (OabMonitorada oab : alvos) {
				try {
					var r = pipeline.processar(oab.titularId(), fonte.buscar(oab, de, ate));
					lidos += r.lidos();
					novos += r.novasPublicacoes();
				} catch (RuntimeException e) {
					log.warn("Falha na captura {} (titular {}): {}", fonte.fonte(), oab.titularId(), e.getMessage());
					erros.add("titular " + oab.titularId() + ": " + e.getClass().getSimpleName());
				}
			}
			jdbc.update("update execucao_captura set fim = now(), resultado = ?, itens_lidos = ?, itens_novos = ?, erro = ? where id = ?",
					erros.isEmpty() ? "OK" : "ERRO", lidos, novos, erros.isEmpty() ? null : String.join("; ", erros), id);
			resultado.add(execucao(id));
		}
		auditoria.registrar(atorId, "CAPTURA_EXECUTADA", "captura", null, "de=" + de + " ate=" + ate);
		return resultado;
	}

	/**
	 * Entrada manual de itens de uma fonte (ex.: e-mail ou Domicilio enquanto o conector nao existe), passando pelo
	 * mesmo pipeline: filtro, cruzamento e fila.
	 */
	public PipelineCaptura.Resultado importar(Long atorId, Long titularId, List<DadosBrutos> itens) {
		var r = pipeline.processar(titularId, itens);
		auditoria.registrar(atorId, "CAPTURA_IMPORTADA", "captura", String.valueOf(titularId), "itens=" + itens.size());
		return r;
	}

	public List<Execucao> execucoes() {
		return jdbc.query("select id, fonte, inicio, fim, resultado, itens_lidos, itens_novos, erro from execucao_captura order by id desc limit 100",
				(rs, n) -> new Execucao(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
						rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(), rs.getString(5), rs.getInt(6),
						rs.getInt(7), rs.getString(8)));
	}

	/** Itens ignorados por filtro (nada some sem registro). */
	public List<Map<String, Object>> ignorados() {
		return jdbc.queryForList("select i.id, i.momento, i.regra, i.motivo, b.fonte, b.tribunal, b.data_publicacao, b.titular_id "
				+ "from item_ignorado i join publicacao_bruta b on b.id = i.bruta_id order by i.id desc limit 200");
	}

	private Execucao execucao(Long id) {
		return jdbc.queryForObject("select id, fonte, inicio, fim, resultado, itens_lidos, itens_novos, erro from execucao_captura where id = ?",
				(rs, n) -> new Execucao(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
						rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(), rs.getString(5), rs.getInt(6),
						rs.getInt(7), rs.getString(8)), id);
	}
}
