package br.com.crispimcampos.gestao.atendimento;

import java.time.Instant;
import java.time.LocalDate;

public record AndamentoDto(Long id, Long processoId, LocalDate data, String descricao, String origem, boolean repassado,
		Instant repassadoEm, String repassadoVia) {
}
