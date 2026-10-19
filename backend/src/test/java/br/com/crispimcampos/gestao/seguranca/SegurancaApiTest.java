package br.com.crispimcampos.gestao.seguranca;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.crispimcampos.gestao.contas.MeController;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import br.com.crispimcampos.gestao.contas.DoisFatoresService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, MeController.class, SegurancaApiTest.Relogio.class})
class SegurancaApiTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	UserDetailsService userDetailsService;

	@MockitoBean
	DoisFatoresService doisFatores;

	@org.springframework.boot.test.context.TestConfiguration
	static class Relogio {

		@Bean
		Clock relogio() {
			return Clock.systemUTC();
		}
	}

	@Test
	void apiSemLoginRetorna401() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void usuarioLogadoVeSeusDados() throws Exception {
		var u = new UsuarioLogado(7L, "Fulana Teste", "fulana@exemplo.test", "x", Perfil.ADVOGADO, true);
		mvc.perform(get("/api/me").with(user(u)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.perfil").value("ADVOGADO"))
				.andExpect(jsonPath("$.email").value("fulana@exemplo.test"));
	}

	@Test
	void postSemCsrfEhRecusado() throws Exception {
		mvc.perform(post("/api/login").param("email", "a@b.test").param("senha", "x"))
				.andExpect(status().isForbidden());
	}

	/** Fluxo real da SPA: le o cookie XSRF-TOKEN e devolve o valor bruto no cabecalho X-XSRF-TOKEN. */
	@Test
	void loginValidoComTokenBrutoDoCookieRetorna204() throws Exception {
		var hash = new BCryptPasswordEncoder(12).encode("senha-correta-123");
		var u = new UsuarioLogado(7L, "Fulana Teste", "fulana@exemplo.test", hash, Perfil.SOCIA, true);
		when(userDetailsService.loadUserByUsername("fulana@exemplo.test")).thenReturn(u);

		Cookie cookie = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");

		mvc.perform(post("/api/login")
						.cookie(cookie)
						.header("X-XSRF-TOKEN", cookie.getValue())
						.param("email", "fulana@exemplo.test")
						.param("senha", "senha-correta-123"))
				.andExpect(status().isNoContent());
	}

	@Test
	void loginInvalidoRetorna401ComCsrf() throws Exception {
		when(userDetailsService.loadUserByUsername(any())).thenThrow(new UsernameNotFoundException("x"));
		// Sem o post-processor csrf(): ele troca o repositorio de token do filtro compartilhado e vaza para outros testes.
		Cookie cookie = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		mvc.perform(post("/api/login").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue())
						.param("email", "a@b.test").param("senha", "x"))
				.andExpect(status().isUnauthorized());
	}

	// ---- segundo fator, limite de tentativas e cabecalhos

	private org.springframework.test.web.servlet.ResultActions entrar(String email, String senha, String codigo) throws Exception {
		Cookie cookie = mvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		var req = post("/api/login").cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()).param("email", email).param("senha", senha);
		if (codigo != null) {
			req = req.param("codigo", codigo);
		}
		return mvc.perform(req);
	}

	private void usuarioComSenha(String email, String senha) {
		var hash = new BCryptPasswordEncoder(12).encode(senha);
		when(userDetailsService.loadUserByUsername(email)).thenReturn(new UsuarioLogado(8L, "Fulano", email, hash, Perfil.ADVOGADO, true));
	}

	@Test
	void comSegundoFatorExigeOCodigoDepoisDaSenha() throws Exception {
		String email = "dois.fatores@exemplo.test";
		usuarioComSenha(email, "senha-correta-123");
		when(doisFatores.exigeCodigo(email)).thenReturn(true);
		when(doisFatores.codigoValido(email, "123456")).thenReturn(true);

		entrar(email, "senha-correta-123", null).andExpect(status().isUnauthorized())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-2FA-Obrigatorio", "true"));
		entrar(email, "senha-correta-123", "000000").andExpect(status().isUnauthorized())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("X-2FA-Obrigatorio"));
		entrar(email, "senha-errada", "123456").andExpect(status().isUnauthorized());
		entrar(email, "senha-correta-123", "123456").andExpect(status().isNoContent());
	}

	@Test
	void semSegundoFatorOLoginNaoMudou() throws Exception {
		String email = "um.fator@exemplo.test";
		usuarioComSenha(email, "senha-correta-123");
		when(doisFatores.exigeCodigo(email)).thenReturn(false);
		entrar(email, "senha-correta-123", null).andExpect(status().isNoContent());
	}

	@Test
	void apos5FalhasOLoginDaOMesmoIpEEmailRetorna429() throws Exception {
		String email = "forca.bruta@exemplo.test";
		usuarioComSenha(email, "senha-correta-123");
		for (int i = 0; i < LoginThrottle.MAXIMO; i++) {
			entrar(email, "errada-" + i, null).andExpect(status().isUnauthorized());
		}
		entrar(email, "senha-correta-123", null).andExpect(status().isTooManyRequests())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"));
	}

	@Test
	void pedirOCodigoNaoContaComoFalha() throws Exception {
		String email = "pede.codigo@exemplo.test";
		usuarioComSenha(email, "senha-correta-123");
		when(doisFatores.exigeCodigo(email)).thenReturn(true);
		for (int i = 0; i < LoginThrottle.MAXIMO + 2; i++) {
			entrar(email, "senha-correta-123", null).andExpect(status().isUnauthorized());
		}
		when(doisFatores.codigoValido(email, "654321")).thenReturn(true);
		entrar(email, "senha-correta-123", "654321").andExpect(status().isNoContent());
	}

	@Test
	void cabecalhosDeSegurancaPresentes() throws Exception {
		var h = org.springframework.test.web.servlet.result.MockMvcResultMatchers.header();
		mvc.perform(get("/api/csrf")).andExpect(h.string("X-Frame-Options", "DENY")).andExpect(h.string("X-Content-Type-Options", "nosniff"))
				.andExpect(h.string("Referrer-Policy", "no-referrer"))
				.andExpect(h.string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
	}
}
