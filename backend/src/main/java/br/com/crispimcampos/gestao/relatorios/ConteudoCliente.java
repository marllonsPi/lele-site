package br.com.crispimcampos.gestao.relatorios;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Conteudo do relatorio ao cliente: A LISTA BRANCA (invariante 6). Este tipo e o unico formato que existe para o texto
 * entregue ao cliente; nao ha campo para observacoes internas, prazo interno, conferencias, nomes de executores ou dados
 * de outros clientes, entao nao tem como vazarem por engano.
 */
public record ConteudoCliente(String clienteNome, LocalDate periodoInicio, LocalDate periodoFim, List<ProcessoRel> processos,
		String comentarios, FinanceiroRel financeiro) {

	public record ProcessoRel(String numero, String varaComarca, String tribunal, String tipoAcao, String situacao,
			List<String> partes, List<Item> movimentacoes, List<Item> feitos, List<Item> proximosPassos) {
	}

	/** Linha datada em linguagem simples. */
	public record Item(LocalDate data, String texto) {
	}

	/** So o que o cliente precisa: parcelas pagas e a vencer. */
	public record FinanceiroRel(BigDecimal parcelasPagas, BigDecimal parcelasAVencer) {
	}
}
