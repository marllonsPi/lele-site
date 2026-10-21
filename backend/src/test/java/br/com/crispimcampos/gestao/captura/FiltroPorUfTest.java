package br.com.crispimcampos.gestao.captura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FiltroPorUfTest {

	@Test
	void descobreAUfPeloNumeroCnj() {
		assertThat(UfTribunal.ufs("0001234-56.2026.8.09.0001", null)).containsExactly("GO"); // TJGO
		assertThat(UfTribunal.ufs("00012345620265180001", "TRT18")).containsExactly("GO"); // TRT-18
		assertThat(UfTribunal.ufs("0001234-56.2026.8.07.0001", null)).containsExactly("DF"); // TJDFT
	}

	@Test
	void semNumeroUsaASiglaEOQueNaoSabeFicaVazio() {
		assertThat(UfTribunal.ufs(null, "TJGO")).containsExactly("GO");
		assertThat(UfTribunal.ufs(null, "TRT-18")).containsExactly("GO");
		assertThat(UfTribunal.ufs("0001234-56.2026.4.01.3500", "TRF1")).isEmpty(); // federal: nao da para saber a UF
		assertThat(UfTribunal.ufs(null, null)).isEmpty();
	}

	@Test
	void soTrazerGoIgnoraSoOQueEDeOutroEstado() {
		assertThat(Filtros.ignoraForaDasUfs("GO", "0001234-56.2026.8.07.0001", "TJDFT")).isTrue();
		assertThat(Filtros.ignoraForaDasUfs("GO", "0001234-56.2026.8.09.0001", "TJGO")).isFalse();
		assertThat(Filtros.ignoraForaDasUfs("GO,DF", "0001234-56.2026.8.07.0001", "TJDFT")).isFalse();
		assertThat(Filtros.ignoraForaDasUfs("GO", null, "TRF1")).isFalse(); // nao identificavel: entra
	}
}
