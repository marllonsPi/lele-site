package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Cartao da agenda: bolinha = tipo (cor do tipo), fundo/borda = statusVisual. */
public record AgendaItemDto(Long id, Long processoId, Long clienteId, TipoAgendamento tipo, String corTipo,
		String descricao, Long responsavelId, Long criadoPor, boolean urgente, StatusAgendamento status,
		LocalDate data, LocalTime hora, Integer duracaoMin, Modalidade modalidade, String localOuLink,
		LocalDate inicioPrazo, LocalDate prazoInterno, LocalDate prazoFatal, StatusVisual statusVisual,
		boolean alertaAudiencia, boolean aguardandoConferenciaPrazo) {

	static final int DIAS_ALERTA_AUDIENCIA = 10;

	static AgendaItemDto de(Agendamento a, LocalDate hoje, ZoneId zona) {
		LocalDate data;
		LocalTime hora = null;
		if (a.getInicioEm() != null) {
			var local = a.getInicioEm().atZone(zona);
			data = local.toLocalDate();
			hora = local.toLocalTime();
		} else {
			data = a.getPrazoInterno();
		}
		boolean alerta = a.getTipo() == TipoAgendamento.AUD && !data.isBefore(hoje)
				&& !data.isAfter(hoje.plusDays(DIAS_ALERTA_AUDIENCIA));
		return new AgendaItemDto(a.getId(), a.getProcessoId(), a.getClienteId(), a.getTipo(), a.getTipo().cor(),
				a.getDescricao(), a.getResponsavelId(), a.getCriadoPor(), a.isUrgente(), a.getStatus(), data, hora,
				a.getDuracaoMin(), a.getModalidade(), a.getLocalOuLink(), a.getInicioPrazo(), a.getPrazoInterno(),
				a.getPrazoFatal(), StatusVisual.de(data, hoje), alerta,
				a.getStatus() == StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO);
	}
}
