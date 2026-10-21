package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import java.util.Map;
import java.util.Set;

/**
 * Descobre a UF de um processo pelo numero CNJ (NNNNNNN-DD.AAAA.J.TR.OOOO): J=8 Justica Estadual (TR e o estado) e J=5
 * Trabalho (TR e a regiao do TRT). Sem numero valido, tenta a sigla (TJGO, TRT18). Justica Federal, tribunais superiores
 * e o que nao der para saber devolvem vazio: o filtro nunca descarta o que nao consegue identificar.
 */
final class UfTribunal {

	private static final String[] ESTADUAL = {null, "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
			"PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SE", "SP", "TO"};

	/** Regiao do TRT -> UFs que ela cobre. */
	private static final Map<Integer, Set<String>> TRT = Map.ofEntries(Map.entry(1, Set.of("RJ")), Map.entry(2, Set.of("SP")),
			Map.entry(3, Set.of("MG")), Map.entry(4, Set.of("RS")), Map.entry(5, Set.of("BA")), Map.entry(6, Set.of("PE")),
			Map.entry(7, Set.of("CE")), Map.entry(8, Set.of("PA", "AP")), Map.entry(9, Set.of("PR")),
			Map.entry(10, Set.of("DF", "TO")), Map.entry(11, Set.of("AM", "RR")), Map.entry(12, Set.of("SC")),
			Map.entry(13, Set.of("PB")), Map.entry(14, Set.of("RO", "AC")), Map.entry(15, Set.of("SP")), Map.entry(16, Set.of("MA")),
			Map.entry(17, Set.of("ES")), Map.entry(18, Set.of("GO")), Map.entry(19, Set.of("AL")), Map.entry(20, Set.of("SE")),
			Map.entry(21, Set.of("RN")), Map.entry(22, Set.of("PI")), Map.entry(23, Set.of("MT")), Map.entry(24, Set.of("MS")));

	private UfTribunal() {
	}

	static Set<String> ufs(String numeroProcesso, String sigla) {
		String n = NumeroCnj.normalizar(numeroProcesso);
		if (n.length() == 20) {
			int j = n.charAt(13) - '0';
			int tr = Integer.parseInt(n.substring(14, 16));
			if (j == 8 && tr >= 1 && tr < ESTADUAL.length) {
				return Set.of(ESTADUAL[tr]);
			}
			if (j == 5) {
				return TRT.getOrDefault(tr, Set.of());
			}
			return Set.of();
		}
		String s = Filtros.normalizar(sigla);
		if (s.matches("TJ[A-Z]{2}")) {
			String uf = s.substring(2);
			return java.util.Arrays.asList(ESTADUAL).contains(uf) ? Set.of(uf) : Set.of();
		}
		if (s.matches("TRT\\d{1,2}")) {
			return TRT.getOrDefault(Integer.parseInt(s.substring(3)), Set.of());
		}
		return Set.of();
	}
}
