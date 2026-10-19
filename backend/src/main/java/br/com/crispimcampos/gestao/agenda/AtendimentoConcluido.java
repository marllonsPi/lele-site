package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;

/** Publicado quando um ATD e finalizado; o modulo de atendimento gera o registro (item 13). */
public record AtendimentoConcluido(Long agendamentoId, Long clienteId, Long processoId, Instant inicio, Long atendenteId,
		String canal, String resumo, Long finalizadoPor) {
}
