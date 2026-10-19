package br.com.crispimcampos.gestao.agenda;

import java.time.Instant;

public record AnexoDto(Long id, String nome, long tamanho, Long autorId, Instant criadoEm) {

	static AnexoDto de(Anexo a) {
		return new AnexoDto(a.getId(), a.getNomeOriginal(), a.getTamanho(), a.getAutorId(), a.getCriadoEm());
	}
}
