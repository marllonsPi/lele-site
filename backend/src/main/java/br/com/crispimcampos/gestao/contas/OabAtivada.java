package br.com.crispimcampos.gestao.contas;

/** Evento publicado quando uma OAB passa a valer (nova ou reativada). A captura (Fase 6) passa a monitora-la. */
public record OabAtivada(Long oabId, Long usuarioId, String numero, String uf) {
}
