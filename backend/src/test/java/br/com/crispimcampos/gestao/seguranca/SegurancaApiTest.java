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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, MeController.class})
class SegurancaApiTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	UserDetailsService userDetailsService;

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
}
