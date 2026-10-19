package br.com.crispimcampos.gestao.cadastros;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.seguranca.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CadastroController.class)
@Import({SecurityConfig.class, CadastroController.class})
class CadastroControllerTest {

	@Autowired
	MockMvc mvc;
	@MockitoBean
	CadastroService servico;
	@MockitoBean
	UserDetailsService userDetailsService;

	static UsuarioLogado como(Perfil p) {
		return new UsuarioLogado(7L, "Teste", "t@exemplo.test", "x", p, true);
	}

	static final String PROCESSO_JSON = "{\"numero\":\"00020802520125150049\",\"tribunal\":\"TJDFT\",\"clienteId\":1,\"advogadoId\":3}";

	@Test
	void anonimoRecebe401() throws Exception {
		mvc.perform(get("/api/processos")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
	}

	@Test
	void todoPerfilLeMasSoSociaEscreve() throws Exception {
		for (Perfil p : Perfil.values()) {
			mvc.perform(get("/api/processos").with(user(como(p)))).andExpect(status().isOk());
			mvc.perform(get("/api/clientes").with(user(como(p)))).andExpect(status().isOk());
		}
		for (Perfil p : new Perfil[] {Perfil.ADVOGADO, Perfil.ASSISTENTE, Perfil.ESTAGIARIO}) {
			mvc.perform(post("/api/processos").with(user(como(p))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
					.contentType(MediaType.APPLICATION_JSON).content(PROCESSO_JSON)).andExpect(status().isForbidden());
			mvc.perform(post("/api/clientes/1/desativar").with(user(como(p))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void sociaCriaProcesso() throws Exception {
		mvc.perform(post("/api/processos").with(user(como(Perfil.SOCIA))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON).content(PROCESSO_JSON)).andExpect(status().isCreated());
		verify(servico).criarProcesso(7L, "00020802520125150049", "TJDFT", null, null, null, null, 1L, 3L);
	}

	@Test
	void corpoInvalidoRetorna400() throws Exception {
		mvc.perform(post("/api/processos").with(user(como(Perfil.SOCIA))).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"numero\":\"\"}")).andExpect(status().isBadRequest());
	}
}
