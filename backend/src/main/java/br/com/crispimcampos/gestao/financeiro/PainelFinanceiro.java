package br.com.crispimcampos.gestao.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Painel financeiro das socias: tudo calculado sobre as parcelas (canceladas nao entram). Valores em aberto descontam o parcialmente pago. */
public record PainelFinanceiro(List<MesFinanceiro> meses, long quantidadeVencidas, BigDecimal totalVencido, List<ParcelaLinha> vencidas,
		Periodo aVencer7Dias, Periodo aVencer30Dias, List<ReceitaArea> porArea) {

	public record MesFinanceiro(String mes, BigDecimal recebido, BigDecimal emAberto) {
	}

	public record ParcelaLinha(Long parcelaId, Long contratoId, Long clienteId, String cliente, String contrato, String area, String rotulo,
			LocalDate vencimento, BigDecimal valorEmAberto, long diasAtraso) {
	}

	public record Periodo(long quantidade, BigDecimal total, List<ParcelaLinha> parcelas) {
	}

	/** {@code area} nula = contrato sem area ainda. */
	public record ReceitaArea(String area, BigDecimal recebido, BigDecimal emAberto) {
	}

	/** Usado pelo alerta diario. */
	public record ResumoVencidas(long quantidade, BigDecimal total) {
	}
}
