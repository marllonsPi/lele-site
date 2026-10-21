package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sugere a area de atuacao a partir do "tipo de acao" em texto livre. Ordem importa: a primeira e a sugerida. */
final class AreaPorTexto {

	private static final Map<AreaAtuacao, List<String>> PALAVRAS = new LinkedHashMap<>();

	static {
		PALAVRAS.put(AreaAtuacao.TRABALHISTA, List.of("TRABALH", "RECLAMATORIA", "VERBAS RESCISORIAS", "VINCULO EMPREGATICIO"));
		PALAVRAS.put(AreaAtuacao.PREVIDENCIARIO, List.of("PREVIDENCI", "INSS", "APOSENTAD", "BPC", "LOAS", "AUXILIO DOENCA", "PENSAO POR MORTE"));
		PALAVRAS.put(AreaAtuacao.FAMILIA, List.of("ALIMENT", "PENSAO", "GUARDA", "DIVORCIO", "FAMILIA", "UNIAO ESTAVEL", "ADOCAO", "PATERNIDADE", "VISITA"));
		PALAVRAS.put(AreaAtuacao.SUCESSAO, List.of("INVENTARIO", "SUCESSAO", "SUCESSOES", "SOBREPARTILHA", "TESTAMENTO", "ALVARA"));
		PALAVRAS.put(AreaAtuacao.IMOBILIARIO, List.of("USUCAPIAO", "IMOVEL", "IMOBILIARI", "LOCACAO", "DESPEJO", "REINTEGRACAO DE POSSE", "ADJUDICACAO"));
		PALAVRAS.put(AreaAtuacao.CONDOMINIAL, List.of("CONDOMIN"));
		PALAVRAS.put(AreaAtuacao.MILITAR, List.of("MILITAR"));
		PALAVRAS.put(AreaAtuacao.CRIMINAL, List.of("PENAL", "CRIMINAL", "FLAGRANTE", "HABEAS", "PRISAO", "INQUERITO"));
		PALAVRAS.put(AreaAtuacao.ADMINISTRATIVO, List.of("ADMINISTRATIV", "CONCURSO", "DETRAN", "SERVIDOR PUBLICO", "LICITACAO", "REGULARIZACAO"));
		PALAVRAS.put(AreaAtuacao.CIVEL, List.of("CIVEL", "BANCARIO", "SAUDE", "CONSUMIDOR", "INDENIZ", "COBRANCA", "CONTRATO", "PLANO DE SAUDE"));
	}

	private AreaPorTexto() {
	}

	static List<AreaAtuacao> sugerir(String tipoAcao) {
		String t = Texto.normalizar(tipoAcao);
		List<AreaAtuacao> r = new ArrayList<>();
		for (var e : PALAVRAS.entrySet()) {
			if (e.getValue().stream().anyMatch(t::contains)) {
				r.add(e.getKey());
			}
		}
		return r;
	}
}
