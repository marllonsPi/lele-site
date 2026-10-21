package br.com.crispimcampos.gestao.importacao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Resumo e detalhe de uma importacao (somente socias). {@code contagens}: tipo -> acao -> quantidade. */
public record ImportacaoDto(Long id, String origem, String modo, String arquivoNome, String status, OffsetDateTime criadoEm,
		OffsetDateTime confirmadoEm, Long confirmadoPor, Map<String, Map<String, Long>> contagens, long comDivergencia,
		List<String> pendencias, Map<String, Object> relatorio, List<ItemPrevia> itens) {
}
