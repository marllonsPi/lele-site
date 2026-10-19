package br.com.crispimcampos.gestao.acesso;

/** Uma celula da matriz: {@code observadorId} ve o que esta em nome de {@code titularId} no {@code escopo}. */
public record ParVisibilidade(Long observadorId, Long titularId, Escopo escopo) {
}
