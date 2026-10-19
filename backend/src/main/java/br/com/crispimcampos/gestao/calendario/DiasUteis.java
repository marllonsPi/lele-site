package br.com.crispimcampos.gestao.calendario;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Calculo de dia util sem framework nem banco (invariante 5). */
public final class DiasUteis {

	private DiasUteis() {
	}

	public static boolean util(LocalDate data, Set<LocalDate> naoUteis) {
		DayOfWeek d = data.getDayOfWeek();
		return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY && !naoUteis.contains(data);
	}

	/** Data de trabalho: {@code n} dias uteis antes de {@code fatal} (o proprio fatal nao conta). */
	public static LocalDate subtrair(LocalDate fatal, int n, Set<LocalDate> naoUteis) {
		LocalDate d = fatal;
		int contados = 0;
		while (contados < n) {
			d = d.minusDays(1);
			if (util(d, naoUteis)) {
				contados++;
			}
		}
		return d;
	}
}
