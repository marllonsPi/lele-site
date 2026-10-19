package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;

public record ObservacaoDto(Long id, Long autorId, String texto, Instant criadoEm) {

	static ObservacaoDto de(ObservacaoAgendamento o) {
		return new ObservacaoDto(o.getId(), o.getAutorId(), o.getTexto(), o.getCriadoEm());
	}
}
