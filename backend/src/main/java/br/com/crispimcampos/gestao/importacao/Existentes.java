package br.com.crispimcampos.gestao.importacao;

import java.util.Map;
import java.util.Optional;

/** Consulta ao que ja existe no sistema, para classificar cada item da previa. Cada mapa traz "id" e os campos comparaveis. */
interface Existentes {

	/** Cliente por CPF/CNPJ (preferencial) ou, na falta dele, pelo nome normalizado. Traz "id", "nome", "documento", "areas". */
	Optional<Map<String, Object>> cliente(String documento, String nomeNormalizado);

	Optional<Map<String, Object>> contrato(String chaveOrigem);

	/** Contratos do cliente (qualquer origem), com "id", "chave", "valorTotal", "cnj" (do processo vinculado), "dataAssinatura" e "formaPagamento". */
	java.util.List<Map<String, Object>> contratosDoCliente(Long clienteId);

	/** Documento ja importado do Drive pelo id do arquivo: "id" e "driveModificadoEm". */
	Optional<Map<String, Object>> documentoPorDrive(String driveId);

	Optional<Map<String, Object>> parcela(String chaveOrigem);

	Optional<Map<String, Object>> processo(String cnj);

	Optional<Map<String, Object>> despesaEscritorio(String chaveOrigem);
}
