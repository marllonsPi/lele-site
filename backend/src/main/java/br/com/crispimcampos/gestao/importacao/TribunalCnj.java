package br.com.crispimcampos.gestao.importacao;

import java.util.Map;

/** Tribunal inferido dos campos J (segmento) e TR (tribunal) do numero CNJ de 20 digitos; null quando nao se sabe. */
final class TribunalCnj {

	private static final Map<String, String> ESTADUAL = Map.ofEntries(Map.entry("01", "TJAC"), Map.entry("02", "TJAL"),
			Map.entry("03", "TJAP"), Map.entry("04", "TJAM"), Map.entry("05", "TJBA"), Map.entry("06", "TJCE"), Map.entry("07", "TJDFT"),
			Map.entry("08", "TJES"), Map.entry("09", "TJGO"), Map.entry("10", "TJMA"), Map.entry("11", "TJMT"), Map.entry("12", "TJMS"),
			Map.entry("13", "TJMG"), Map.entry("14", "TJPA"), Map.entry("15", "TJPB"), Map.entry("16", "TJPR"), Map.entry("17", "TJPE"),
			Map.entry("18", "TJPI"), Map.entry("19", "TJRJ"), Map.entry("20", "TJRN"), Map.entry("21", "TJRS"), Map.entry("22", "TJRO"),
			Map.entry("23", "TJRR"), Map.entry("24", "TJSC"), Map.entry("25", "TJSE"), Map.entry("26", "TJSP"), Map.entry("27", "TJTO"));

	private TribunalCnj() {
	}

	static String de(String cnj) {
		if (cnj == null || cnj.length() != 20) {
			return null;
		}
		char j = cnj.charAt(13);
		String tr = cnj.substring(14, 16);
		return switch (j) {
			case '8' -> ESTADUAL.get(tr);
			case '5' -> tr.equals("00") ? "TST" : "TRT-" + Integer.parseInt(tr);
			case '4' -> tr.equals("90") ? "CJF" : "TRF" + Integer.parseInt(tr);
			case '6' -> "TRE-" + tr;
			case '3' -> "STJ";
			case '1' -> "STF";
			case '7' -> "STM";
			case '9' -> "Justica Militar Estadual " + tr;
			default -> null;
		};
	}
}
