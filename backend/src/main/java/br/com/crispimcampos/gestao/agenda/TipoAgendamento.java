package br.com.crispimcampos.gestao.agenda;

/** Siglas da spec (item 5). Prazos seguem a regra dos 3 dias uteis; AUD e ATD tem data e hora proprias (Fase 4). */
public enum TipoAgendamento {
	CTR("preto"), INI("rosa"), CON("laranja"), MAN("cinza"), AUD("roxo"), REC("marrom"), ATD("azul");

	private final String cor;

	TipoAgendamento(String cor) {
		this.cor = cor;
	}

	public String cor() {
		return cor;
	}

	public boolean ehPrazo() {
		return this != AUD && this != ATD;
	}
}
