package br.com.crispimcampos.gestao.calendario;

import java.time.LocalDate;

public record FeriadoDto(Long id, EscopoFeriado escopo, String referencia, TipoFeriado tipo, LocalDate dataInicial,
		LocalDate dataFinal, String descricao, boolean ativo) {

	static FeriadoDto de(Feriado f) {
		return new FeriadoDto(f.getId(), f.getEscopo(), f.getReferencia(), f.getTipo(), f.getDataInicial(),
				f.getDataFinal(), f.getDescricao(), f.isAtivo());
	}
}
