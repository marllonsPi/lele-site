package br.com.crispimcampos.gestao.relatorios;

import br.com.crispimcampos.gestao.agenda.TipoAgendamento;

/** Texto em linguagem simples para o cliente, sem jargao nem sigla interna. */
final class Linguagem {

	private Linguagem() {
	}

	static String feito(TipoAgendamento t) {
		return switch (t) {
			case INI -> "Peticao inicial protocolada";
			case CON -> "Contestacao protocolada";
			case MAN -> "Manifestacao protocolada nos autos";
			case REC -> "Recurso protocolado";
			case AUD -> "Audiencia realizada";
			default -> "Providencia realizada";
		};
	}

	static String providencia(TipoAgendamento t) {
		return switch (t) {
			case INI -> "Elaboracao e protocolo da peticao inicial";
			case CON -> "Elaboracao e protocolo da contestacao";
			case MAN -> "Elaboracao e protocolo de manifestacao nos autos";
			case REC -> "Elaboracao e protocolo de recurso";
			default -> "Providencia prevista";
		};
	}
}
