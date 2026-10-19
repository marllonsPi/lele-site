package br.com.crispimcampos.gestao.alertas;

import java.time.Instant;
import java.time.LocalDate;

public record NotificacaoDto(Long id, TipoAlerta tipo, Long destinatarioId, String canal, LocalDate dataRef, String assunto,
		String corpo, String status, int tentativas, String erro, Instant enviadoEm) {
}
