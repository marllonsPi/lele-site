package br.com.crispimcampos.gestao.captura;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class CapturaUnidadeTest {

	// ---- filtros (item 3: TRT exceto TRT-10)

	@Test
	void ignoraTrtExcetoTrt10() {
		assertThat(Filtros.ignora("TRT-1", "TRT", "TRT-10")).isTrue();
		assertThat(Filtros.ignora("TRT15", "TRT", "TRT-10")).isTrue();
		assertThat(Filtros.ignora("trt 2", "TRT", "TRT-10")).isTrue();
		assertThat(Filtros.ignora("TRT-10", "TRT", "TRT-10")).isFalse();
		assertThat(Filtros.ignora("TRT10", "TRT", "TRT-10")).as("mesma sigla sem hifen").isFalse();
		assertThat(Filtros.ignora("TJDFT", "TRT", "TRT-10")).isFalse();
		assertThat(Filtros.ignora(null, "TRT", "TRT-10")).isFalse();
		assertThat(Filtros.ignora("TRT-3", "TRT", null)).as("sem excecoes ignora todos").isTrue();
	}

	// ---- parser DJEN (formato verificado em 2026-10-19; dados totalmente ficticios)

	static Map<String, Object> item(Object id, String data, String numero, String texto) {
		Map<String, Object> m = new HashMap<>();
		m.put("id", id);
		m.put("data_disponibilizacao", data);
		m.put("numero_processo", numero);
		m.put("siglaTribunal", "TJXX");
		m.put("texto", texto);
		m.put("hash", "abc123");
		return m;
	}

	@Test
	void parserLeItensValidosEContaInvalidos() {
		Map<String, Object> resposta = Map.of("status", "success", "count", 3, "items", List.of(
				item(11, "2026-10-15", "00020802520125150049", "Intimacao de teste"),
				item(null, "2026-10-15", "1", "sem id"),
				item(13, "data-ruim", "1", "data invalida")));
		var pg = DjenParser.parse(resposta);
		assertThat(pg.recebidos()).isEqualTo(3);
		assertThat(pg.invalidos()).isEqualTo(2);
		assertThat(pg.total()).isEqualTo(3);
		assertThat(pg.itens()).hasSize(1);
		DadosBrutos d = pg.itens().get(0);
		assertThat(d.fonte()).isEqualTo(Fonte.DJEN);
		assertThat(d.idExterno()).isEqualTo("11");
		assertThat(d.dataPublicacao()).isEqualTo(LocalDate.parse("2026-10-15"));
		assertThat(d.tribunal()).isEqualTo("TJXX");
	}

	@Test
	void parserToleraRespostaVaziaOuNula() {
		assertThat(DjenParser.parse(null).itens()).isEmpty();
		assertThat(DjenParser.parse(Map.of("status", "success", "count", 0)).itens()).isEmpty();
	}

	@Test
	void chaveUsaNumeroCnjOuHashEResumoLimpaHtml() {
		var comNumero = new DadosBrutos(Fonte.DJEN, "1", "0002080-25.2012.5.15.0049", "TJXX", LocalDate.now(), "x", "h".repeat(30));
		assertThat(PipelineCaptura.chave(comNumero)).isEqualTo("00020802520125150049");
		var semNumero = new DadosBrutos(Fonte.EMAIL, "2", null, "TJXX", LocalDate.now(), "x", "abcdef0123456789zzzz");
		assertThat(PipelineCaptura.chave(semNumero)).isEqualTo("SN-abcdef0123456789");
		assertThat(PipelineCaptura.resumo("<p>Ola   <b>mundo</b></p>")).isEqualTo("Ola mundo");
		assertThat(PipelineCaptura.resumo("a".repeat(500))).hasSize(400).endsWith("...");
	}

	@Test
	void conectorTrataRespostaVaziaOuForaDoPadraoComoFalha() throws IOException {
		for (String corpo : new String[] {"{}", "{\"status\":\"error\"}", "{\"status\":\"success\"}"}) {
			HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			srv.createContext("/api/v1/comunicacao", ex -> {
				byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
				ex.getResponseHeaders().add("Content-Type", "application/json");
				ex.sendResponseHeaders(200, b.length);
				ex.getResponseBody().write(b);
				ex.close();
			});
			srv.start();
			try {
				var fonte = new DjenFonte("http://127.0.0.1:" + srv.getAddress().getPort());
				org.assertj.core.api.Assertions.assertThatThrownBy(() -> fonte.buscar(new OabMonitorada(1L, "90001", "DF"), LocalDate.now(), LocalDate.now()))
						.isInstanceOf(IllegalStateException.class);
			} finally {
				srv.stop(0);
			}
		}
	}

	// ---- conector DJEN contra um servidor HTTP local (sem rede externa)

	@Test
	void conectorPaginaEEnviaOsParametrosDaOab() throws IOException {
		List<String> consultas = new CopyOnWriteArrayList<>();
		HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		srv.createContext("/api/v1/comunicacao", ex -> {
			consultas.add(ex.getRequestURI().getRawQuery());
			boolean primeira = ex.getRequestURI().getRawQuery().contains("pagina=1");
			List<String> itens = new ArrayList<>();
			int n = primeira ? DjenFonte.POR_PAGINA : 2;
			for (int i = 0; i < n; i++) {
				int id = (primeira ? 0 : 1000) + i;
				itens.add("{\"id\":" + id + ",\"data_disponibilizacao\":\"2026-10-15\",\"numero_processo\":\"00020802520125150049\","
						+ "\"siglaTribunal\":\"TJXX\",\"texto\":\"texto ficticio " + id + "\",\"hash\":\"h" + id + "\"}");
			}
			byte[] corpo = ("{\"status\":\"success\",\"count\":" + (DjenFonte.POR_PAGINA + 2) + ",\"items\":[" + String.join(",", itens) + "]}")
					.getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(200, corpo.length);
			ex.getResponseBody().write(corpo);
			ex.close();
		});
		srv.start();
		try {
			var fonte = new DjenFonte("http://127.0.0.1:" + srv.getAddress().getPort());
			var itens = fonte.buscar(new OabMonitorada(1L, "90001", "DF"), LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-17"));
			assertThat(itens).hasSize(DjenFonte.POR_PAGINA + 2);
			assertThat(consultas).hasSize(2);
			assertThat(consultas.get(0)).contains("numeroOab=90001").contains("ufOab=DF")
					.contains("dataDisponibilizacaoInicio=2026-10-10").contains("dataDisponibilizacaoFim=2026-10-17");
		} finally {
			srv.stop(0);
		}
	}
}
