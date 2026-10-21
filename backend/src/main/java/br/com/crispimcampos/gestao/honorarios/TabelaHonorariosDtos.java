package br.com.crispimcampos.gestao.honorarios;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Dados das tabelas de honorarios (somente socias, exceto o catalogo sem valores). */
public final class TabelaHonorariosDtos {

	private TabelaHonorariosDtos() {
	}

	public record VersaoDto(Long id, String tabela, String seccional, String referencia, BigDecimal valorUrh, BigDecimal fatorEscritorio,
			BigDecimal descontoPadrao, BigDecimal descontoTrabalhista, BigDecimal descontoPacote, boolean vigente, String arquivoNome, Instant criadoEm,
			Instant substituidaEm, long itens) {
	}

	public record ItemDto(Long id, Long versaoId, String tabela, String area, String tipoServico, String fase, String chaveServico, String itemOab,
			BigDecimal valorUrh, BigDecimal valor, boolean derivado, BigDecimal descontoPercentual, BigDecimal valorSugerido, String exitoTexto,
			BigDecimal exitoMin, BigDecimal exitoMax, String parcelamento, String observacoes, boolean ativo) {
	}

	/** Entrada de criacao/edicao de item. Na tabela da OAB, {@code valor} = minimo em R$; na do escritorio = valor de tabela. */
	public record DadosItem(String area, String tipoServico, String fase, String itemOab, BigDecimal valorUrh, BigDecimal valor, BigDecimal descontoPercentual,
			BigDecimal valorSugerido, String exitoTexto, BigDecimal exitoMin, BigDecimal exitoMax, String parcelamento, String observacoes) {
	}

	/** Servico sem valores: o que o advogado ve para escolher o servico pretendido. */
	public record ServicoCatalogo(String chave, String area, String tipoServico, String fase) {
	}

	public record ResultadoImportacao(Long versaoOabId, Long versaoEscritorioId, int servicos, int semArea, int semValor, List<String> avisos) {
	}
}
