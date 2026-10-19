package br.com.crispimcampos.gestao.captura;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Conector do DJEN por numero de OAB (API publica de comunicacoes). */
@Component
class DjenFonte implements FonteDePublicacoes {

	static final int POR_PAGINA = 100;
	static final int MAXIMO_PAGINAS = 50;

	private final RestClient http;

	DjenFonte(@Value("${app.captura.djen.url:https://comunicaapi.pje.jus.br}") String base) {
		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		fabrica.setReadTimeout(Duration.ofSeconds(30));
		this.http = RestClient.builder().baseUrl(base).requestFactory(fabrica).build();
	}

	@Override
	public Fonte fonte() {
		return Fonte.DJEN;
	}

	@Override
	public List<DadosBrutos> buscar(OabMonitorada oab, LocalDate de, LocalDate ate) {
		List<DadosBrutos> todos = new ArrayList<>();
		for (int pagina = 1; pagina <= MAXIMO_PAGINAS; pagina++) {
			final int p = pagina;
			Map<String, Object> resposta = http.get()
					.uri(u -> u.path("/api/v1/comunicacao")
							.queryParam("numeroOab", oab.numero()).queryParam("ufOab", oab.uf())
							.queryParam("dataDisponibilizacaoInicio", de).queryParam("dataDisponibilizacaoFim", ate)
							.queryParam("itensPorPagina", POR_PAGINA).queryParam("pagina", p).build())
					.retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
					});
			if (resposta == null || !"success".equals(String.valueOf(resposta.get("status"))) || !(resposta.get("items") instanceof java.util.List)) {
				// fonte fora do ar ou formato inesperado: e falha (registrada), nunca "0 itens"
				throw new IllegalStateException("Resposta inesperada do DJEN");
			}
			DjenParser.Pagina pg = DjenParser.parse(resposta);
			todos.addAll(pg.itens());
			if (pg.recebidos() < POR_PAGINA || todos.size() + pg.invalidos() >= pg.total()) {
				break;
			}
		}
		return todos;
	}
}
