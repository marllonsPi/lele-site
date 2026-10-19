package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;

public record ConferenciaDto(Long id, String etapa, String resultado, String motivo, Long usuarioId, Instant criadoEm) {

	static ConferenciaDto de(Conferencia c) {
		return new ConferenciaDto(c.getId(), c.getEtapa().name(), c.getResultado().name(), c.getMotivo(), c.getUsuarioId(),
				c.getCriadoEm());
	}
}
