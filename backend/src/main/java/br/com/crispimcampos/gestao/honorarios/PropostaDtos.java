package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.honorarios.SugestaoHonorarios.Quadro;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PropostaDtos {

	private PropostaDtos() {
	}

	public record ItemProposta(Long id, String chave, String area, String tipoServico, String fase, BigDecimal minimoOab, BigDecimal valorTabela, BigDecimal valorSugerido,
			String exitoTexto) {
	}

	/**
	 * Proposta de honorarios de um atendimento: valor sugerido (guardado) x valor efetivamente fechado. {@code quadro}: o que o
	 * advogado ve no atendimento. {@code abaixoDaOab}: o fechado ficou abaixo do minimo da OAB (exige justificativa).
	 */
	public record PropostaDto(Long id, Long clienteId, String clienteNome, Long agendamentoId, Long advogadoId, String seccional, String status, BigDecimal valorMinimoOab,
			BigDecimal valorSugerido, BigDecimal descontoPacote, BigDecimal valorFechado, String justificativaTipo, String justificativa, BigDecimal entrada, Integer parcelas,
			LocalDate primeiroVencimento, String formaPagamento, LocalDate dataAssinatura, boolean contratoLancado, Instant criadoEm, Instant fechadoEm, List<ItemProposta> itens,
			Quadro quadro, boolean abaixoDaOab, BigDecimal diferencaAbaixoOab, boolean tabelaOabVigente) {
	}

	public record Fechamento(BigDecimal valorFechado, BigDecimal entrada, Integer parcelas, LocalDate primeiroVencimento, String formaPagamento, String justificativaTipo,
			String justificativa) {
	}
}
