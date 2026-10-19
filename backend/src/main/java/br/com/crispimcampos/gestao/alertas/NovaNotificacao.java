package br.com.crispimcampos.gestao.alertas;

/** Alerta a enviar; {@code escopoId} = agendamento (alertas de prazo/compromisso) ou responsavel (resumo; 0 = consolidado). */
public record NovaNotificacao(TipoAlerta tipo, long escopoId, Long destinatarioId, String assunto, String corpo) {
}
