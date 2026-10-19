package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Conflitos de agenda (item 5), dominio puro. Dois compromissos conflitam quando os horarios se sobrepoem e
 * (a) envolvem o mesmo advogado (AUD ou ATD) ou (b) ambos sao audiencias no escritorio.
 * So avisa: bloquear depende da pendencia 5.
 */
public final class Conflitos {

	public enum Tipo {
		MESMO_ADVOGADO, AUDIENCIAS_NO_ESCRITORIO
	}

	/** Compromisso com hora marcada; fim exclusivo. */
	public record Intervalo(Long agendamentoId, TipoAgendamento tipo, Long advogadoId, Instant inicio, Instant fim) {
	}

	public record Aviso(Tipo tipo, TipoAgendamento comTipo, Instant inicio, Instant fim) {
	}

	private Conflitos() {
	}

	public static boolean sobrepoe(Intervalo a, Intervalo b) {
		return a.inicio().isBefore(b.fim()) && b.inicio().isBefore(a.fim());
	}

	public static List<Aviso> detectar(Intervalo novo, List<Intervalo> existentes) {
		List<Aviso> avisos = new ArrayList<>();
		for (Intervalo e : existentes) {
			if (e.agendamentoId() != null && e.agendamentoId().equals(novo.agendamentoId()) || !sobrepoe(novo, e)) {
				continue;
			}
			if (e.advogadoId().equals(novo.advogadoId())) {
				avisos.add(new Aviso(Tipo.MESMO_ADVOGADO, e.tipo(), e.inicio(), e.fim()));
			} else if (novo.tipo() == TipoAgendamento.AUD && e.tipo() == TipoAgendamento.AUD) {
				avisos.add(new Aviso(Tipo.AUDIENCIAS_NO_ESCRITORIO, e.tipo(), e.inicio(), e.fim()));
			}
		}
		return avisos;
	}
}
