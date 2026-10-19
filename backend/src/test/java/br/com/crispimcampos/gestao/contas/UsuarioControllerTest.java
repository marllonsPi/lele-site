package br.com.crispimcampos.gestao.contas;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.crispimcampos.gestao.seguranca.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, UsuarioController.class})
class UsuarioControllerTest {

	@Autowired
	MockMvc mvc;
	@MockitoBean
	UsuarioService servico;
	@MockitoBean
	UserDetailsService userDetailsService;

	static UsuarioLogado como(Perfil p) {
		return new UsuarioLogado(7L, "Teste", "t@exemplo.test", "x", p, true);
	}

	@Test
	void anonimoRecebe401() throws Exception {
		mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
	}

	@Test
	void somenteSociaGereUsuarios() throws Exception {
		for (Perfil p : new Perfil[] {Perfil.ADVOGADO, Perfil.ASSISTENTE, Perfil.ESTAGIARIO}) {
			mvc.perform(get("/api/usuarios").with(user(como(p)))).andExpect(status().isForbidden());
			mvc.perform(post("/api/usuarios/3/desativar").with(user(como(p))).with(csrf())).andExpect(status().isForbidden());
			mvc.perform(post("/api/usuarios/3/oabs").with(user(como(p))).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("{\"numero\":\"1\",\"uf\":\"DF\",\"principal\":false}"))
					.andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/usuarios").with(user(como(Perfil.SOCIA)))).andExpect(status().isOk());
	}

	@Test
	void sociaDesativaERegistraQuemFez() throws Exception {
		mvc.perform(post("/api/usuarios/3/desativar").with(user(como(Perfil.SOCIA))).with(csrf()))
				.andExpect(status().isNoContent());
		verify(servico).desativar(7L, 3L);
	}

	@Test
	void validacaoRecusaCorpoInvalido() throws Exception {
		mvc.perform(post("/api/usuarios").with(user(como(Perfil.SOCIA))).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"\",\"email\":\"x\",\"senha\":\"\"}"))
				.andExpect(status().isBadRequest());
	}
}
