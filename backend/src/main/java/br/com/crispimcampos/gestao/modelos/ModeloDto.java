package br.com.crispimcampos.gestao.modelos;

import java.time.Instant;

public record ModeloDto(Long id, String tipo, String areaDireito, String titulo, String nomeArquivo, long tamanho, boolean ativo,
		Long criadoPor, Instant criadoEm) {
}
