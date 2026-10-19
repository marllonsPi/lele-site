package br.com.crispimcampos.gestao.captura;

import java.time.LocalDate;
import java.util.List;

/** Contrato unico dos conectores (arquitetura 5): cada fonte e um adaptador isolado; falha de uma nao derruba as outras. */
public interface FonteDePublicacoes {

	Fonte fonte();

	/** Itens da OAB publicados no periodo (inclusive). Lanca excecao em caso de falha da fonte. */
	List<DadosBrutos> buscar(OabMonitorada oab, LocalDate de, LocalDate ate);

	/**
	 * Fontes ligadas a conta da propria pessoa (e-mail) nao dependem de OAB: devolvem os titulares que consultam.
	 * {@code null} (padrao) significa "uma consulta por OAB monitorada".
	 */
	default List<Long> titularesProprios() {
		return null;
	}
}
