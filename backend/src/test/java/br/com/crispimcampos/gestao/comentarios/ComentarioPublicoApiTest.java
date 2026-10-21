package br.com.crispimcampos.gestao.comentarios;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.crispimcampos.gestao.contas.DoisFatoresService;
import br.com.crispimcampos.gestao.seguranca.SecurityConfig;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Parte publica: abre sem login e sem CSRF; abrir o link do e-mail nao decide; robos (isca) e excesso sao barrados. */
@WebMvcTest(ComentarioPublicoController.class)
@Import({SecurityConfig.class, LimiteDeEnvio.class, ComentarioPublicoApiTest.Relogio.class})
class ComentarioPublicoApiTest {

	@Autowired
	MockMvc mvc;
	@MockitoBean
	ComentarioService servico;
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

	private static final String JSON = "{\"nome\":\"Ana\",\"email\":\"ana@exemplo.test\",\"mensagem\":\"Muito bom o atendimento, obrigada.\"}";

	@Test
	void visitanteEnviaSemLoginENemCsrf() throws Exception {
		mvc.perform(post("/api/public/comentarios").contentType(MediaType.APPLICATION_JSON).content(JSON)).andExpect(status().isAccepted());
		verify(servico).receber("Ana", "ana@exemplo.test", "Muito bom o atendimento, obrigada.");
	}

	@Test
	void iscaPreenchidaNaoGravaMasResponderIgual() throws Exception {
		mvc.perform(post("/api/public/comentarios").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Bot\",\"email\":\"b@exemplo.test\",\"mensagem\":\"spam spam spam spam\",\"website\":\"http://x\"}"))
				.andExpect(status().isAccepted());
		verify(servico, never()).receber(any(), any(), any());
	}

	@Test
	void quartoEnvioNaMesmaHoraRecebe429() throws Exception {
		for (int i = 0; i < 3; i++) {
			mvc.perform(post("/api/public/comentarios").header("X-Forwarded-For", "203.0.113.9").contentType(MediaType.APPLICATION_JSON).content(JSON))
					.andExpect(status().isAccepted());
		}
		mvc.perform(post("/api/public/comentarios").header("X-Forwarded-For", "203.0.113.9").contentType(MediaType.APPLICATION_JSON).content(JSON))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void abrirOLinkNaoDecideESoOBotaoDecide() throws Exception {
		var c = new ComentarioService.Fila(5L, "Ana <b>", "ana@exemplo.test", "Texto <script>x</script>", "PENDENTE", java.time.Instant.now(), null, null, true);
		when(servico.porToken("tokentokentokentokentoken")).thenReturn(Optional.of(c));
		mvc.perform(get("/api/public/comentarios/moderar").param("t", "tokentokentokentokentoken"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Aprovar e publicar")))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>x"))));
		verify(servico, never()).decidirPorToken(any(), org.mockito.ArgumentMatchers.anyBoolean());

		when(servico.decidirPorToken("tokentokentokentokentoken", true)).thenReturn(Optional.of("APROVADO"));
		mvc.perform(post("/api/public/comentarios/moderar").contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("t", "tokentokentokentokentoken").param("acao", "aprovar"))
				.andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("aprovado")));
	}

	@Test
	void listagemPublicaNaoPedeLogin() throws Exception {
		when(servico.aprovados()).thenReturn(java.util.List.of());
		mvc.perform(get("/api/public/comentarios")).andExpect(status().isOk());
	}
}
