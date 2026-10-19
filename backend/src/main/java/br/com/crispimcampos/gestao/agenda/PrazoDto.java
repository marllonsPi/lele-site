package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;
import java.time.LocalDate;

public record PrazoDto(Long id, Long processoId, TipoAgendamento tipo, String cor, String descricao,
		Long responsavelId, Long lancadoPor, boolean urgente, StatusAgendamento status, LocalDate inicioPrazo,
		LocalDate prazoInterno, LocalDate prazoFatal, Long conferidoPor, Instant conferidoEm) {

	static PrazoDto de(Agendamento a) {
		return new PrazoDto(a.getId(), a.getProcessoId(), a.getTipo(), a.getTipo().cor(), a.getDescricao(),
				a.getResponsavelId(), a.getLancadoPor(), a.isUrgente(), a.getStatus(), a.getInicioPrazo(),
				a.getPrazoInterno(), a.getPrazoFatal(), a.getConferidoPor(), a.getConferidoEm());
	}
}
