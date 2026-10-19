package br.com.crispimcampos.gestao.relatorios;

import java.time.Instant;
import java.time.LocalDate;

public record RelatorioClienteDto(Long id, Long clienteId, LocalDate periodoInicio, LocalDate periodoFim, String estado,
		boolean incluirFinanceiro, ConteudoCliente conteudo, Long criadoPor, Instant criadoEm, Long aprovadoPor, Instant aprovadoEm,
		Long enviadoPor, Instant enviadoEm, String canal) {
}
