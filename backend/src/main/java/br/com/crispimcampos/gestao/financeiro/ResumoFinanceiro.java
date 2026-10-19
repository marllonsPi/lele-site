package br.com.crispimcampos.gestao.financeiro;

import java.math.BigDecimal;

/** Posicao financeira de um cliente ou processo. */
public record ResumoFinanceiro(BigDecimal contratado, BigDecimal parcelasPagas, BigDecimal parcelasAVencer,
		BigDecimal parcelasEmAtraso, long quantidadeEmAtraso, BigDecimal custasEDespesas, BigDecimal reembolsavel,
		BigDecimal exitoPrevisto, BigDecimal exitoRecebido, BigDecimal sucumbenciaPrevista, BigDecimal sucumbenciaRecebida) {
}
