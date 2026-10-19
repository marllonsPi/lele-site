package br.com.crispimcampos.gestao.calendario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiasUteisTest {

	static LocalDate d(String s) {
		return LocalDate.parse(s);
	}

	@Test
	void semFeriadoVoltaTresDiasUteis() {
		// quarta 2026-10-07 -> terca, segunda, sexta anterior
		assertThat(DiasUteis.subtrair(d("2026-10-07"), 3, Set.of())).isEqualTo(d("2026-10-02"));
	}

	@Test
	void fimDeSemanaNaoContaEFatalNaSegunda() {
		// segunda 2026-10-05 -> sexta(1), quinta(2), quarta(3)
		assertThat(DiasUteis.subtrair(d("2026-10-05"), 3, Set.of())).isEqualTo(d("2026-09-30"));
	}

	@Test
	void feriadoNaSemanaEPulado() {
		// quarta 2026-10-14; segunda 12/10 e feriado -> terca(1), sexta(2), quinta(3)
		assertThat(DiasUteis.subtrair(d("2026-10-14"), 3, Set.of(d("2026-10-12")))).isEqualTo(d("2026-10-08"));
	}

	@Test
	void suspensaoLongaEViradaDeAno() {
		Set<LocalDate> naoUteis = new HashSet<>();
		for (LocalDate x = d("2025-12-20"); !x.isAfter(d("2026-01-20")); x = x.plusDays(1)) {
			naoUteis.add(x);
		}
		// quarta 2026-01-21 -> volta ate sexta 19/12 (1), quinta 18 (2), quarta 17 (3)
		assertThat(DiasUteis.subtrair(d("2026-01-21"), 3, naoUteis)).isEqualTo(d("2025-12-17"));
	}

	@Test
	void resultadoSempreEDiaUtil() {
		Set<LocalDate> naoUteis = Set.of(d("2026-11-02"), d("2026-11-20"));
		for (LocalDate fatal = d("2026-11-01"); fatal.isBefore(d("2026-12-01")); fatal = fatal.plusDays(1)) {
			assertThat(DiasUteis.util(DiasUteis.subtrair(fatal, 3, naoUteis), naoUteis)).isTrue();
		}
	}

	@Test
	void zeroDiasDevolveOProprioFatal() {
		assertThat(DiasUteis.subtrair(d("2026-10-10"), 0, Set.of())).isEqualTo(d("2026-10-10"));
	}
}
