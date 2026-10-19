package br.com.crispimcampos.gestao.captura;

import java.time.LocalDate;

/** Publicado quando a socia classifica uma publicacao; vira andamento a repassar ao cliente (item 13). */
public record PublicacaoClassificada(Long publicacaoId, Long processoId, LocalDate data, String resumo, Long classificadoPor) {
}
