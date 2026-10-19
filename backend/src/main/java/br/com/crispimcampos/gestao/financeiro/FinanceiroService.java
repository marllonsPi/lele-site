package br.com.crispimcampos.gestao.financeiro;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Financeiro (item 12). Acesso restrito as socias em TODA operacao, inclusive leitura (invariante 1); nao usa a matriz de
 * visibilidade. Consultas ao resumo ficam na auditoria (trilha de acesso a dado sensivel).
 */
@Service
public class FinanceiroService {

	private final JdbcTemplate jdbc;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	FinanceiroService(JdbcTemplate jdbc, AuditoriaService auditoria, Clock relogio) {
		this.jdbc = jdbc;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	static void exigirSocia(UsuarioLogado u) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("O financeiro e restrito as socias");
		}
	}

	// ---- contratos e parcelas

	@Transactional
	public Long criarContrato(UsuarioLogado ator, Long clienteId, Long processoId, String descricao, BigDecimal valorTotal,
			int parcelas, LocalDate primeiroVencimento) {
		exigirSocia(ator);
		existeCliente(clienteId);
		verificarProcesso(clienteId, processoId);
		var previstas = Parcelamento.dividir(valorTotal, parcelas, primeiroVencimento);
		Long id = jdbc.queryForObject("insert into contrato_honorarios (cliente_id, processo_id, descricao, valor_total) values (?, ?, ?, ?) returning id",
				Long.class, clienteId, processoId, descricao.trim(), valorTotal);
		for (var p : previstas) {
			jdbc.update("insert into parcela (contrato_id, numero, vencimento, valor) values (?, ?, ?, ?)", id, p.numero(),
					java.sql.Date.valueOf(p.vencimento()), p.valor());
		}
		auditoria.registrar(ator.id(), "CONTRATO_CRIADO", "contrato_honorarios", String.valueOf(id), "parcelas=" + parcelas);
		return id;
	}

	@Transactional
	public void pagarParcela(UsuarioLogado ator, Long parcelaId, LocalDate data, BigDecimal valorPago) {
		exigirSocia(ator);
		if (valorPago == null || valorPago.signum() <= 0) {
			throw new IllegalArgumentException("Informe o valor pago");
		}
		int n = jdbc.update("update parcela set pago_em = ?, valor_pago = ? where id = ? and pago_em is null",
				java.sql.Date.valueOf(data == null ? LocalDate.now(relogio) : data), valorPago, parcelaId);
		if (n == 0) {
			throw new IllegalArgumentException("Parcela inexistente ou ja paga");
		}
		auditoria.registrar(ator.id(), "PARCELA_PAGA", "parcela", String.valueOf(parcelaId), null);
	}

	/** Parcelas de um contrato com a situacao calculada: PAGA, A_VENCER ou EM_ATRASO. */
	@Transactional(readOnly = true)
	public List<Map<String, Object>> parcelas(UsuarioLogado ator, Long contratoId) {
		exigirSocia(ator);
		return jdbc.queryForList("select id, numero, vencimento, valor, pago_em, valor_pago, "
				+ "case when pago_em is not null then 'PAGA' when vencimento < ? then 'EM_ATRASO' else 'A_VENCER' end as situacao "
				+ "from parcela where contrato_id = ? order by numero", java.sql.Date.valueOf(LocalDate.now(relogio)), contratoId);
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> contratos(UsuarioLogado ator, Long clienteId) {
		exigirSocia(ator);
		return jdbc.queryForList("select id, cliente_id, processo_id, descricao, valor_total, ativo from contrato_honorarios where cliente_id = ? order by id", clienteId);
	}

	// ---- despesas e honorarios variaveis

	@Transactional
	public Long registrarDespesa(UsuarioLogado ator, Long clienteId, Long processoId, String tipo, String descricao, BigDecimal valor,
			LocalDate data, boolean reembolsavel) {
		exigirSocia(ator);
		existeCliente(clienteId);
		verificarProcesso(clienteId, processoId);
		if (!List.of("CUSTAS", "DESPESA").contains(tipo)) {
			throw new IllegalArgumentException("Tipo invalido (CUSTAS ou DESPESA)");
		}
		if (valor == null || valor.signum() <= 0) {
			throw new IllegalArgumentException("Valor deve ser positivo");
		}
		Long id = jdbc.queryForObject("insert into despesa (cliente_id, processo_id, tipo, descricao, valor, data, reembolsavel) values (?, ?, ?, ?, ?, ?, ?) returning id",
				Long.class, clienteId, processoId, tipo, descricao.trim(), valor, java.sql.Date.valueOf(data == null ? LocalDate.now(relogio) : data), reembolsavel);
		auditoria.registrar(ator.id(), "DESPESA_REGISTRADA", "despesa", String.valueOf(id), tipo);
		return id;
	}

	@Transactional
	public Long registrarHonorarioVariavel(UsuarioLogado ator, Long clienteId, Long processoId, String tipo, String descricao,
			BigDecimal valorPrevisto) {
		exigirSocia(ator);
		existeCliente(clienteId);
		verificarProcesso(clienteId, processoId);
		if (!List.of("EXITO", "SUCUMBENCIA").contains(tipo)) {
			throw new IllegalArgumentException("Tipo invalido (EXITO ou SUCUMBENCIA)");
		}
		if (valorPrevisto == null || valorPrevisto.signum() <= 0) {
			throw new IllegalArgumentException("Valor previsto deve ser positivo");
		}
		Long id = jdbc.queryForObject("insert into honorario_variavel (cliente_id, processo_id, tipo, descricao, valor_previsto) values (?, ?, ?, ?, ?) returning id",
				Long.class, clienteId, processoId, tipo, descricao.trim(), valorPrevisto);
		auditoria.registrar(ator.id(), "HONORARIO_VARIAVEL_REGISTRADO", "honorario_variavel", String.valueOf(id), tipo);
		return id;
	}

	@Transactional
	public void receberHonorarioVariavel(UsuarioLogado ator, Long id, LocalDate data, BigDecimal valor) {
		exigirSocia(ator);
		if (valor == null || valor.signum() <= 0) {
			throw new IllegalArgumentException("Informe o valor recebido");
		}
		int n = jdbc.update("update honorario_variavel set recebido_em = ?, valor_recebido = ? where id = ? and recebido_em is null",
				java.sql.Date.valueOf(data == null ? LocalDate.now(relogio) : data), valor, id);
		if (n == 0) {
			throw new IllegalArgumentException("Honorario inexistente ou ja recebido");
		}
		auditoria.registrar(ator.id(), "HONORARIO_RECEBIDO", "honorario_variavel", String.valueOf(id), null);
	}

	// ---- resumos

	/** Nao e readOnly: a consulta grava a trilha de acesso. */
	@Transactional
	public ResumoFinanceiro resumoDoCliente(UsuarioLogado ator, Long clienteId) {
		exigirSocia(ator);
		existeCliente(clienteId);
		auditoria.registrar(ator.id(), "FINANCEIRO_CONSULTADO", "cliente", String.valueOf(clienteId), null);
		return resumo("c.cliente_id = ?", "d.cliente_id = ?", "h.cliente_id = ?", clienteId);
	}

	@Transactional
	public ResumoFinanceiro resumoDoProcesso(UsuarioLogado ator, Long processoId) {
		exigirSocia(ator);
		auditoria.registrar(ator.id(), "FINANCEIRO_CONSULTADO", "processo", String.valueOf(processoId), null);
		return resumo("c.processo_id = ?", "d.processo_id = ?", "h.processo_id = ?", processoId);
	}

	private ResumoFinanceiro resumo(String fc, String fd, String fh, Long id) {
		java.sql.Date hoje = java.sql.Date.valueOf(LocalDate.now(relogio));
		BigDecimal contratado = soma("select coalesce(sum(c.valor_total), 0) from contrato_honorarios c where c.ativo and " + fc, id);
		BigDecimal pagas = soma("select coalesce(sum(p.valor_pago), 0) from parcela p join contrato_honorarios c on c.id = p.contrato_id where c.ativo and p.pago_em is not null and " + fc, id);
		BigDecimal aVencer = jdbc.queryForObject("select coalesce(sum(p.valor), 0) from parcela p join contrato_honorarios c on c.id = p.contrato_id "
				+ "where c.ativo and p.pago_em is null and p.vencimento >= ? and " + fc, BigDecimal.class, hoje, id);
		var atraso = jdbc.queryForMap("select coalesce(sum(p.valor), 0) as total, count(*) as qtd from parcela p join contrato_honorarios c on c.id = p.contrato_id "
				+ "where c.ativo and p.pago_em is null and p.vencimento < ? and " + fc, hoje, id);
		BigDecimal despesas = soma("select coalesce(sum(d.valor), 0) from despesa d where " + fd, id);
		BigDecimal reembolsavel = soma("select coalesce(sum(d.valor), 0) from despesa d where d.reembolsavel and " + fd, id);
		return new ResumoFinanceiro(contratado, pagas, aVencer, (BigDecimal) atraso.get("total"), ((Number) atraso.get("qtd")).longValue(),
				despesas, reembolsavel,
				soma("select coalesce(sum(h.valor_previsto), 0) from honorario_variavel h where h.tipo = 'EXITO' and " + fh, id),
				soma("select coalesce(sum(h.valor_recebido), 0) from honorario_variavel h where h.tipo = 'EXITO' and " + fh, id),
				soma("select coalesce(sum(h.valor_previsto), 0) from honorario_variavel h where h.tipo = 'SUCUMBENCIA' and " + fh, id),
				soma("select coalesce(sum(h.valor_recebido), 0) from honorario_variavel h where h.tipo = 'SUCUMBENCIA' and " + fh, id));
	}

	private BigDecimal soma(String sql, Long id) {
		return jdbc.queryForObject(sql, BigDecimal.class, id);
	}

	private void existeCliente(Long clienteId) {
		Integer n = jdbc.queryForObject("select count(*) from cliente where id = ? and ativo", Integer.class, clienteId);
		if (n == null || n == 0) {
			throw new IllegalArgumentException("Cliente nao encontrado ou desativado");
		}
	}

	/** O processo, quando informado, deve ser do mesmo cliente. */
	private void verificarProcesso(Long clienteId, Long processoId) {
		if (processoId == null) {
			return;
		}
		Integer n = jdbc.queryForObject("select count(*) from processo where id = ? and cliente_id = ?", Integer.class, processoId, clienteId);
		if (n == null || n == 0) {
			throw new IllegalArgumentException("O processo nao pertence a este cliente");
		}
	}
}
