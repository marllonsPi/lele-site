package br.com.crispimcampos.gestao.atendimento;

import br.com.crispimcampos.gestao.agenda.AtendimentoConcluido;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.captura.PublicacaoClassificada;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.sql.Array;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atendimento (item 13): registro de cada contato com o cliente (gerado pelo ATD concluido ou avulso) e andamentos
 * pendentes de repasse. Leitura sempre limitada aos clientes/processos que o usuario enxerga (invariante 1).
 * Resumos de atendimento sao dados do cliente: ficam so neste modulo e nunca entram no relatorio ao cliente por si.
 */
@Service
public class AtendimentoService {

	static final Set<String> CANAIS = Set.of("PRESENCIAL", "ONLINE", "TELEFONE", "WHATSAPP", "EMAIL", "OUTRO");

	private static final RowMapper<AtendimentoDto> ATD = (rs, n) -> new AtendimentoDto(rs.getLong("id"), rs.getLong("cliente_id"),
			(Long) rs.getObject("processo_id"), rs.getTimestamp("momento").toInstant(), rs.getString("canal"),
			rs.getLong("atendente_id"), rs.getString("resumo"), rs.getString("origem"), (Long) rs.getObject("agendamento_id"));

	private static final RowMapper<AndamentoDto> AND = (rs, n) -> new AndamentoDto(rs.getLong("id"), rs.getLong("processo_id"),
			rs.getDate("data").toLocalDate(), rs.getString("descricao"), rs.getString("origem"), rs.getBoolean("repassado"),
			rs.getTimestamp("repassado_em") == null ? null : rs.getTimestamp("repassado_em").toInstant(), rs.getString("repassado_via"));

	private final JdbcTemplate jdbc;
	private final CadastroService cadastros;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	AtendimentoService(JdbcTemplate jdbc, CadastroService cadastros, AuditoriaService auditoria, Clock relogio) {
		this.jdbc = jdbc;
		this.cadastros = cadastros;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	// ---- atendimentos

	@Transactional
	public AtendimentoDto registrarAvulso(UsuarioLogado ator, Long clienteId, Long processoId, String canal, LocalDateTime momento,
			String resumo, Long atendenteId) {
		if (ator.perfil() == Perfil.ESTAGIARIO) {
			throw new SecurityException("Estagiario nao registra atendimento avulso");
		}
		if (!CANAIS.contains(canal)) {
			throw new IllegalArgumentException("Canal invalido");
		}
		if (resumo == null || resumo.isBlank()) {
			throw new IllegalArgumentException("Informe o resumo do atendimento");
		}
		if (!cadastros.clienteVisivel(ator, clienteId)) {
			throw new AtendimentoNaoEncontradoException("Cliente nao encontrado");
		}
		if (processoId != null) {
			cadastros.buscarProcesso(ator, processoId);
		}
		Instant quando = (momento == null ? LocalDateTime.now(relogio) : momento).atZone(relogio.getZone()).toInstant();
		Long id = jdbc.queryForObject("insert into atendimento (cliente_id, processo_id, momento, canal, atendente_id, resumo, origem, criado_por) "
				+ "values (?, ?, ?, ?, ?, ?, 'AVULSO', ?) returning id", Long.class, clienteId, processoId,
				java.sql.Timestamp.from(quando), canal, atendenteId != null ? atendenteId : ator.id(), resumo.trim(), ator.id());
		auditoria.registrar(ator.id(), "ATENDIMENTO_REGISTRADO", "atendimento", String.valueOf(id), null);
		return jdbc.queryForObject("select * from atendimento where id = ?", ATD, id);
	}

	@Transactional(readOnly = true)
	public List<AtendimentoDto> listar(UsuarioLogado ator, Long clienteId) {
		if (clienteId != null) {
			if (!cadastros.clienteVisivel(ator, clienteId)) {
				throw new AtendimentoNaoEncontradoException("Cliente nao encontrado");
			}
			return jdbc.query("select * from atendimento where cliente_id = ? order by momento desc", ATD, clienteId);
		}
		List<Long> visiveis = cadastros.listarClientes(ator).stream().map(c -> c.id()).collect(Collectors.toList());
		if (visiveis.isEmpty()) {
			return List.of();
		}
		return jdbc.query(con -> {
			var ps = con.prepareStatement("select * from atendimento where cliente_id = any(?) order by momento desc limit 500");
			Array a = con.createArrayOf("bigint", visiveis.toArray());
			ps.setArray(1, a);
			return ps;
		}, ATD);
	}

	/** ATD finalizado gera o registro automaticamente; refinalizar (apos devolucao) atualiza o resumo. */
	@EventListener
	@Transactional
	void aoConcluirAtendimento(AtendimentoConcluido e) {
		jdbc.update("insert into atendimento (cliente_id, processo_id, momento, canal, atendente_id, resumo, origem, agendamento_id, criado_por) "
				+ "values (?, ?, ?, ?, ?, ?, 'AGENDAMENTO', ?, ?) on conflict (agendamento_id) do update set resumo = excluded.resumo",
				e.clienteId(), e.processoId(), java.sql.Timestamp.from(e.inicio()), e.canal(), e.atendenteId(), e.resumo(),
				e.agendamentoId(), e.finalizadoPor());
	}

	// ---- andamentos pendentes de repasse

	@Transactional
	public AndamentoDto registrarAndamento(UsuarioLogado ator, Long processoId, java.time.LocalDate data, String descricao) {
		if (ator.perfil() == Perfil.ESTAGIARIO) {
			throw new SecurityException("Estagiario nao registra andamento");
		}
		cadastros.buscarProcesso(ator, processoId);
		if (descricao == null || descricao.isBlank()) {
			throw new IllegalArgumentException("Informe a descricao do andamento");
		}
		Long id = jdbc.queryForObject("insert into andamento (processo_id, data, descricao, origem, criado_por) values (?, ?, ?, 'MANUAL', ?) returning id",
				Long.class, processoId, java.sql.Date.valueOf(data == null ? java.time.LocalDate.now(relogio) : data), descricao.trim(), ator.id());
		auditoria.registrar(ator.id(), "ANDAMENTO_REGISTRADO", "andamento", String.valueOf(id), null);
		return jdbc.queryForObject("select * from andamento where id = ?", AND, id);
	}

	/** Andamentos ainda nao repassados dos processos que o usuario enxerga. */
	@Transactional(readOnly = true)
	public List<AndamentoDto> pendentesDeRepasse(UsuarioLogado ator) {
		List<Long> processos = cadastros.listarProcessos(ator, null).stream().map(p -> p.id()).collect(Collectors.toList());
		if (processos.isEmpty()) {
			return List.of();
		}
		return jdbc.query(con -> {
			var ps = con.prepareStatement("select * from andamento where processo_id = any(?) and not repassado order by data desc, id desc");
			Array a = con.createArrayOf("bigint", processos.toArray());
			ps.setArray(1, a);
			return ps;
		}, AND);
	}

	@Transactional
	public AndamentoDto marcarRepassado(UsuarioLogado ator, Long id, String via) {
		AndamentoDto a = jdbc.query("select * from andamento where id = ?", AND, id).stream().findFirst()
				.orElseThrow(() -> new AtendimentoNaoEncontradoException("Andamento nao encontrado"));
		cadastros.buscarProcesso(ator, a.processoId());
		if (a.repassado()) {
			throw new IllegalStateException("Andamento ja repassado");
		}
		jdbc.update("update andamento set repassado = true, repassado_em = now(), repassado_via = ?, repassado_por = ? where id = ?",
				via == null || via.isBlank() ? "MANUAL" : via.trim(), ator.id(), id);
		auditoria.registrar(ator.id(), "ANDAMENTO_REPASSADO", "andamento", String.valueOf(id), null);
		return jdbc.queryForObject("select * from andamento where id = ?", AND, id);
	}

	/** Chamado pelo relatorio ao cliente (Fase 10) ao enviar: marca os andamentos incluidos como repassados. */
	@Transactional
	public int marcarRepassadosPorRelatorio(List<Long> ids, String via, Long porUsuario) {
		if (ids.isEmpty()) {
			return 0;
		}
		return jdbc.update(con -> {
			var ps = con.prepareStatement("update andamento set repassado = true, repassado_em = now(), repassado_via = ?, repassado_por = ? "
					+ "where id = any(?) and not repassado");
			ps.setString(1, via);
			ps.setLong(2, porUsuario);
			ps.setArray(3, con.createArrayOf("bigint", ids.toArray()));
			return ps;
		});
	}

	/** Publicacao classificada vira andamento a repassar (um por publicacao). */
	@EventListener
	@Transactional
	void aoClassificarPublicacao(PublicacaoClassificada e) {
		jdbc.update("insert into andamento (processo_id, data, descricao, origem, publicacao_id, criado_por) values (?, ?, ?, 'PUBLICACAO', ?, ?) "
				+ "on conflict (publicacao_id) do nothing", e.processoId(), java.sql.Date.valueOf(e.data()),
				e.resumo() == null || e.resumo().isBlank() ? "Publicacao recebida no processo." : e.resumo(), e.publicacaoId(),
				e.classificadoPor());
	}
}
