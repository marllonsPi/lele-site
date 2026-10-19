package br.com.crispimcampos.gestao.agenda;

import java.time.LocalDate;

/** Cor de status do cartao na agenda (item 7); a bolinha do tipo e outra coisa. */
public enum StatusVisual {
	VERMELHO, AMARELO, VERDE, NENHUM;

	public static final int DIAS_VENCIDO_VISIVEL = 30;

	/** Vencido ate 30 dias atras = vermelho; hoje = amarelo; amanha = verde; demais = sem cor. */
	public static StatusVisual de(LocalDate data, LocalDate hoje) {
		if (data.isBefore(hoje)) {
			return data.isBefore(hoje.minusDays(DIAS_VENCIDO_VISIVEL)) ? NENHUM : VERMELHO;
		}
		if (data.equals(hoje)) {
			return AMARELO;
		}
		return data.equals(hoje.plusDays(1)) ? VERDE : NENHUM;
	}
}
