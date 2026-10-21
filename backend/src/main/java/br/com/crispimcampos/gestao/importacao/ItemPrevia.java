package br.com.crispimcampos.gestao.importacao;

import java.util.List;
import java.util.Map;

/**
 * Um registro da previa: o que sera criado/alterado, as duas versoes quando ja existe algo (importada x existente) e as
 * divergencias para conferencia. {@code ajustes} guarda correcoes feitas pela socia na previa (area, vencimento etc.).
 */
public record ItemPrevia(Long id, int ordem, Tipo tipo, String chave, String rotulo, Acao acao, List<String> divergencias,
		Map<String, Object> importado, Map<String, Object> existente, Decisao decisao, Map<String, Object> ajustes,
		Resultado resultado, String motivo) {

	public enum Tipo { CLIENTE, PROCESSO, CONTRATO, PARCELA, DESPESA, DOCUMENTO }

	/** CRIAR: nao existe no sistema. CONFLITO: existe e difere (mostrar lado a lado). SEM_MUDANCA: igual. IGNORAR: nao importavel. */
	public enum Acao { CRIAR, CONFLITO, SEM_MUDANCA, IGNORAR }

	public enum Decisao { APLICAR, MANTER }

	public enum Resultado { APLICADO, IGNORADO, ERRO }

	/** Valor efetivo: ajuste da socia, senao o importado. */
	Object valor(String campo) {
		return ajustes.containsKey(campo) ? ajustes.get(campo) : importado.get(campo);
	}

	/** Decisao efetiva: CRIAR aplica por padrao; CONFLITO mantem o existente por padrao (nunca sobrescreve sem confirmacao). */
	boolean aplicar() {
		if (acao == Acao.CRIAR) {
			return decisao != Decisao.MANTER;
		}
		return acao == Acao.CONFLITO && decisao == Decisao.APLICAR;
	}
}
