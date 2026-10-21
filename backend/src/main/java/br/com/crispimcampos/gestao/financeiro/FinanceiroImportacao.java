package br.com.crispimcampos.gestao.financeiro;

import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gravacao de lancamentos financeiros vindos de importacao (planilha, Drive). Quem chama (modulo importacao) ja exigiu socia;
 * aqui so o SQL, com a origem e a chave de origem que tornam a importacao idempotente.
 */
@Component
public class FinanceiroImportacao {

	private final JdbcTemplate jdbc;

	FinanceiroImportacao(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record DadosContrato(Long clienteId, Long processoId, String descricao, BigDecimal valorTotal, AreaAtuacao area,
			String origem, String chave, String formaPagamento, BigDecimal percentualExito, String situacaoOrigem,
			Integer numeroParcelas, boolean aConferir, LocalDate dataAssinatura) {
	}

	@Transactional
	public Long criarContrato(DadosContrato d) {
		return jdbc.queryForObject("insert into contrato_honorarios (cliente_id, processo_id, descricao, valor_total, area, origem, chave_origem, "
				+ "forma_pagamento, percentual_exito, situacao_origem, numero_parcelas, a_conferir, data_assinatura) "
				+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class, d.clienteId(), d.processoId(), limitar(d.descricao(), 300),
				d.valorTotal(), d.area() == null ? null : d.area().name(), d.origem(), d.chave(), d.formaPagamento(), d.percentualExito(),
				limitar(d.situacaoOrigem(), 40), d.numeroParcelas(), d.aConferir(), data(d.dataAssinatura()));
	}

	/** Atualiza so os campos que a importacao controla; nunca troca cliente nem descricao. */
	@Transactional
	public void atualizarContrato(Long id, BigDecimal valorTotal, AreaAtuacao area, String formaPagamento, BigDecimal percentualExito,
			String situacaoOrigem) {
		jdbc.update("update contrato_honorarios set valor_total = ?, area = coalesce(?, area), forma_pagamento = ?, percentual_exito = ?, "
				+ "situacao_origem = ? where id = ?", valorTotal, area == null ? null : area.name(), formaPagamento, percentualExito,
				limitar(situacaoOrigem, 40), id);
	}

	/** Complemento vindo do contrato do Drive: preenche o que falta; so troca valor/forma ja existentes com {@code sobrescrever}. */
	@Transactional
	public void complementarContrato(Long id, BigDecimal valorTotal, LocalDate dataAssinatura, String formaPagamento, Integer numeroParcelas, boolean sobrescrever) {
		if (sobrescrever && valorTotal != null && valorTotal.signum() > 0) {
			jdbc.update("update contrato_honorarios set valor_total = ? where id = ?", valorTotal, id);
		} else if (valorTotal != null && valorTotal.signum() > 0) {
			jdbc.update("update contrato_honorarios set valor_total = ? where id = ? and valor_total = 0", valorTotal, id);
		}
		jdbc.update("update contrato_honorarios set data_assinatura = coalesce(data_assinatura, ?), numero_parcelas = coalesce(numero_parcelas, ?), "
				+ "forma_pagamento = case when ? or forma_pagamento is null or forma_pagamento = '' then coalesce(?, forma_pagamento) else forma_pagamento end where id = ?",
				data(dataAssinatura), numeroParcelas, sobrescrever, formaPagamento, id);
	}

	public record DadosParcela(Long contratoId, int numero, LocalDate vencimento, BigDecimal valor, String situacao, BigDecimal valorPago,
			LocalDate pagoEm, String formaPagamento, String rotulo, String origem, String chave) {
	}

	@Transactional
	public Long criarParcela(DadosParcela d) {
		return jdbc.queryForObject("insert into parcela (contrato_id, numero, vencimento, valor, situacao, valor_pago, pago_em, forma_pagamento, "
				+ "rotulo, origem, chave_origem) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class, d.contratoId(), d.numero(),
				data(d.vencimento()), d.valor(), d.situacao(), d.valorPago(), data(d.pagoEm()), d.formaPagamento(), limitar(d.rotulo(), 80),
				d.origem(), d.chave());
	}

	@Transactional
	public void atualizarParcela(Long id, LocalDate vencimento, BigDecimal valor, String situacao, BigDecimal valorPago) {
		jdbc.update("update parcela set vencimento = ?, valor = ?, situacao = ?, valor_pago = ? where id = ?", data(vencimento), valor, situacao,
				valorPago, id);
	}

	@Transactional
	public Long criarDespesaEscritorio(LocalDate data, String descricao, String categoria, BigDecimal valor, String forma, String origem,
			String chave) {
		return jdbc.queryForObject("insert into despesa_escritorio (data, descricao, categoria, valor, forma_pagamento, origem, chave_origem) "
				+ "values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class, data(data), limitar(descricao, 300), limitar(categoria, 80), valor,
				limitar(forma, 30), origem, chave);
	}

	@Transactional
	public Long criarHonorarioDeExito(Long clienteId, Long processoId, String descricao, BigDecimal valorPrevisto) {
		return jdbc.queryForObject("insert into honorario_variavel (cliente_id, processo_id, tipo, descricao, valor_previsto) "
				+ "values (?, ?, 'EXITO', ?, ?) returning id", Long.class, clienteId, processoId, limitar(descricao, 300), valorPrevisto);
	}

	private static java.sql.Date data(LocalDate d) {
		return d == null ? null : java.sql.Date.valueOf(d);
	}

	private static String limitar(String s, int max) {
		return s == null ? null : s.length() > max ? s.substring(0, max) : s;
	}
}
