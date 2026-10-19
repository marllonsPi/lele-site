package br.com.crispimcampos.gestao.atendimento;

import java.time.Instant;

public record AtendimentoDto(Long id, Long clienteId, Long processoId, Instant momento, String canal, Long atendenteId,
		String resumo, String origem, Long agendamentoId) {
}
