package br.com.crispimcampos.gestao.documentos;

import java.time.Instant;

public record DocumentoClienteDto(Long id, Long clienteId, TipoDocumento tipo, String nome, long tamanho, boolean restritoSocias, String origem,
		Instant criadoEm) {
}
