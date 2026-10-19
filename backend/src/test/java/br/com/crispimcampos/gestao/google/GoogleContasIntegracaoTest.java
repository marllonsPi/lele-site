package br.com.crispimcampos.gestao.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.crispimcampos.gestao.contas.UsuarioService;
import br.com.crispimcampos.gestao.captura.CapturaService;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Fluxo OAuth completo contra Postgres real e um "Google" falso local. Nenhum dado real. */
@SpringBootTest
@ActiveProfiles("dev")
class GoogleContasIntegracaoTest {

	static final EmbeddedPostgres PG = iniciarBanco();
	static final HttpServer GOOGLE = iniciarGoogle();
	static final AtomicBoolean REFRESH_REVOGADO = new AtomicBoolean();
	static final AtomicInteger LOTE = new AtomicInteger();
	static final AtomicInteger REVOGACOES = new AtomicInteger();
	static final String SEGREDO_REFRESH = "refresh-token-ficticio-123";

	static EmbeddedPostgres iniciarBanco() {
		try {
			return EmbeddedPostgres.builder().setDataDirectory(Files.createTempDirectory("pg-google")).start();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static void responder(com.sun.net.httpserver.HttpExchange ex, int status, String corpo) throws IOException {
		byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(status, b.length);
		ex.getResponseBody().write(b);
		ex.close();
	}

	static HttpServer iniciarGoogle() {
		try {
			HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			s.createContext("/token", ex -> {
				String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				if (corpo.contains("grant_type=refresh_token")) {
					if (REFRESH_REVOGADO.get()) {
						responder(ex, 400, "{\"error\":\"invalid_grant\"}");
					} else {
						responder(ex, 200, "{\"access_token\":\"acesso-ficticio\",\"expires_in\":3600}");
					}
				} else if (corpo.contains("code=codigo-sem-gmail")) {
					responder(ex, 200, "{\"access_token\":\"a\",\"refresh_token\":\"r\",\"expires_in\":3600,\"scope\":\"openid email\"}");
				} else if (corpo.contains("code=codigo-ok") && corpo.contains("code_verifier=")) {
					responder(ex, 200, "{\"access_token\":\"acesso-ficticio\",\"refresh_token\":\"" + SEGREDO_REFRESH
							+ "\",\"expires_in\":3600,\"scope\":\"openid email " + GoogleContasService.ESCOPO_GMAIL + "\"}");
				} else {
					responder(ex, 400, "{\"error\":\"invalid_grant\"}");
				}
			});
			s.createContext("/revoke", ex -> {
				REVOGACOES.incrementAndGet();
				responder(ex, 200, "{}");
			});
			s.createContext("/userinfo", ex -> responder(ex, 200, "{\"email\":\"pessoa.ficticia@exemplo.test\"}"));
			s.createContext("/gmail/v1/users/me/messages", ex -> {
				String caminho = ex.getRequestURI().getPath();
				if (caminho.endsWith("/messages")) {
					responder(ex, 200, "{\"messages\":[{\"id\":\"msg" + LOTE.get() + "\"}]}");
				} else {
					String texto = Base64.getUrlEncoder().withoutPadding().encodeToString(("Texto ficticio da intimacao " + LOTE.get()).getBytes(StandardCharsets.UTF_8));
					responder(ex, 200, "{\"id\":\"msg" + LOTE.get() + "\",\"internalDate\":\"1760000000000\",\"payload\":{\"mimeType\":\"text/plain\","
							+ "\"headers\":[{\"name\":\"Subject\",\"value\":\"Aviso ficticio\"}],\"body\":{\"data\":\"" + texto + "\"}}}");
				}
			});
			s.start();
			return s;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		String g = "http://127.0.0.1:" + GOOGLE.getAddress().getPort();
		r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
		r.add("spring.datasource.username", () -> "postgres");
		r.add("spring.datasource.password", () -> "postgres");
		r.add("STORAGE_DIR", () -> "target/teste-storage-google");
		r.add("app.captura.djen.url", () -> "http://127.0.0.1:9");
		r.add("app.google.client-id", () -> "cliente-ficticio");
		r.add("app.google.client-secret", () -> "segredo-ficticio");
		r.add("app.google.redirect-uri", () -> "https://exemplo.test/api/google/callback");
		r.add("app.google.token-key", GoogleUnidadeTest::chave);
		r.add("app.google.token-url", () -> g + "/token");
		r.add("app.google.revoke-url", () -> g + "/revoke");
		r.add("app.google.userinfo-url", () -> g + "/userinfo");
		r.add("app.google.gmail-url", () -> g);
	}

	@AfterAll
	static void encerra() throws IOException {
		GOOGLE.stop(0);
		PG.close();
	}

	@Autowired
	GoogleContasService google;
	@Autowired
	UsuarioService usuarios;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	CapturaService captura;

	Long usuario(String prefixo) {
		return usuarios.listar().stream().filter(u -> u.nome().startsWith(prefixo)).findFirst().orElseThrow().id();
	}

	static String stateDe(String url) {
		return org.springframework.web.util.UriComponentsBuilder.fromUri(URI.create(url)).build().getQueryParams().getFirst("state");
	}

	@Test
	void fluxoCompletoConectaCapturaEDesconecta() {
		LOTE.incrementAndGet(); // id de mensagem novo, para nao colidir com outros testes
		Long eu = usuario("Advogado D");
		Long outra = usuario("Advogada C");
		assertThat(google.status(eu).conectado()).isFalse();

		int conectadasAntes = jdbc.queryForObject("select count(*) from log_auditoria where acao = 'GOOGLE_CONECTAR' and usuario_id = ?", Integer.class, eu);
		String url = google.iniciar(eu, "a@b.test");
		assertThat(url).contains("access_type=offline", "code_challenge_method=S256", "gmail.readonly").doesNotContain("gmail.send");
		google.concluir(eu, "codigo-ok", stateDe(url));

		var st = google.status(eu);
		assertThat(st.conectado()).isTrue();
		assertThat(st.estado()).isEqualTo("ATIVA");
		// isolamento: a outra pessoa nao ve nada
		assertThat(google.status(outra).conectado()).isFalse();
		assertThat(google.usuariosConectados()).containsExactly(eu);

		// refresh token cifrado em repouso
		byte[] cifrado = jdbc.queryForObject("select refresh_token_cifrado from conexao_google where usuario_id = ?", byte[].class, eu);
		assertThat(new String(cifrado, StandardCharsets.ISO_8859_1)).doesNotContain(SEGREDO_REFRESH);

		// captura por e-mail entra na fila do proprio titular, idempotente
		var r1 = captura.executar(eu, LocalDate.now().minusDays(3), LocalDate.now());
		var email = r1.stream().filter(e -> "EMAIL".equals(e.fonte())).findFirst().orElseThrow();
		assertThat(email.resultado()).as(email.toString()).isEqualTo("OK");
		assertThat(email.itensNovos()).as(email.toString()).isEqualTo(1);
		var r2 = captura.executar(eu, LocalDate.now().minusDays(3), LocalDate.now());
		assertThat(r2.stream().filter(e -> "EMAIL".equals(e.fonte())).findFirst().orElseThrow().itensNovos()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from publicacao_bruta where fonte = 'EMAIL' and titular_id = ? and id_externo = ?", Integer.class, eu, "msg" + LOTE.get())).isEqualTo(1);

		// auditoria e imutavel e sem e-mail da conta
		assertThat(jdbc.queryForObject("select count(*) from log_auditoria where acao = 'GOOGLE_CONECTAR' and usuario_id = ?", Integer.class, eu)).isEqualTo(conectadasAntes + 1);
		assertThat(jdbc.queryForObject("select count(*) from log_auditoria where detalhe like '%exemplo.test%'", Integer.class)).isZero();
		assertThatThrownBy(() -> jdbc.update("delete from log_auditoria where acao = 'GOOGLE_CONECTAR'")).hasMessageContaining("somente de insercao");

		// desconectar revoga no Google e apaga o token
		int antes = REVOGACOES.get();
		assertThat(google.desconectar(eu)).isTrue();
		assertThat(REVOGACOES.get()).isEqualTo(antes + 1);
		assertThat(google.status(eu).conectado()).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from conexao_google where usuario_id = ?", Integer.class, eu)).isZero();
		assertThat(google.desconectar(eu)).isFalse();
	}

	@Test
	void estadoDeOutroUsuarioEUsoUnicoSaoRecusados() {
		Long a = usuario("Socia A");
		Long b = usuario("Assistente E");
		String estadoDeA = stateDe(google.iniciar(a, null));
		assertThatThrownBy(() -> google.concluir(b, "codigo-ok", estadoDeA)).isInstanceOf(ConexaoGoogleException.class);
		assertThat(google.status(b).conectado()).isFalse();
		assertThatThrownBy(() -> google.concluir(a, "codigo-ok", "state-inventado")).isInstanceOf(ConexaoGoogleException.class);
		// o dono usa uma vez; a segunda vez o estado ja foi consumido
		google.concluir(a, "codigo-ok", estadoDeA);
		assertThatThrownBy(() -> google.concluir(a, "codigo-ok", estadoDeA)).isInstanceOf(ConexaoGoogleException.class);
		google.desconectar(a);
	}

	@Test
	void semEscopoDoGmailNaoConecta() {
		Long a = usuario("Socia A");
		String state = stateDe(google.iniciar(a, null));
		assertThatThrownBy(() -> google.concluir(a, "codigo-sem-gmail", state)).isInstanceOf(ConexaoGoogleException.class);
		assertThat(google.status(a).conectado()).isFalse();
	}

	@Test
	void tokenRecusadoPeloGoogleMarcaExpiradaENaoQuebraAsOutras() {
		Long a = usuario("Advogado D");
		google.concluir(a, "codigo-ok", stateDe(google.iniciar(a, null)));
		REFRESH_REVOGADO.set(true);
		try {
			// limpa o access token em cache simulando o vencimento: reconecta para forcar caminho de refresh
			var r = captura.executar(a, LocalDate.now().minusDays(1), LocalDate.now());
			var email = r.stream().filter(e -> "EMAIL".equals(e.fonte())).findFirst().orElseThrow();
			// access token ainda em cache (1h): captura ok; forca refresh abaixo
			assertThat(email.resultado()).isIn("OK", "ERRO");
			assertThatThrownBy(() -> forcarRefresh(a)).isInstanceOf(ConexaoGoogleException.class);
			assertThat(google.status(a).estado()).isEqualTo("EXPIRADA");
			assertThat(google.usuariosConectados()).doesNotContain(a);
		} finally {
			REFRESH_REVOGADO.set(false);
			google.desconectar(a);
		}
	}

	private void forcarRefresh(Long a) {
		// descarta o cache: desconectar/reconectar nao serve (limparia o estado); usa reflexao minima via novo servico
		var cache = (java.util.Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(google, "cache");
		cache.clear();
		google.tokenDeAcesso(a);
	}
}
