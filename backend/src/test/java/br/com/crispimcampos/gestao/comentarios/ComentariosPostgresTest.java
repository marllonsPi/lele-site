package br.com.crispimcampos.gestao.comentarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import br.com.crispimcampos.gestao.alertas.EnviadorEmail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Comentarios do site em Postgres real: recebe, avisa as socias, so publica aprovado, primeira decisao vale. */
@SpringBootTest(properties = {"app.bootstrap.socia.nome=Socia Teste", "app.bootstrap.socia.email=socia@exemplo.test",
		"app.bootstrap.socia.senha=senha-socia-teste-1"})
class ComentariosPostgresTest {

	static final EmbeddedPostgres PG = iniciar();

	static EmbeddedPostgres iniciar() {
		try {
			return EmbeddedPostgres.builder().setDataDirectory(Files.createTempDirectory("pg-coment")).start();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void banco(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
		r.add("spring.datasource.username", () -> "postgres");
		r.add("spring.datasource.password", () -> "postgres");
		r.add("STORAGE_DIR", () -> "target/teste-storage-coment");
	}

	@AfterAll
	static void encerra() throws IOException {
		PG.close();
	}

	@Autowired
	ComentarioService servico;
	@Autowired
	JdbcTemplate jdbc;
	@MockitoBean
	EnviadorEmail email;

	@Test
	void pendenteNaoApareceNoSiteEOAprovadoPeloLinkApareceNaHora() {
		long antes = servico.aprovados().size();
		servico.receber("Maria Teste", "maria@exemplo.test", "Atendimento excelente, recomendo muito.");

		assertThat(servico.aprovados()).hasSize((int) antes); // ainda nao decidido: nao publica
		ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
		verify(email, atLeastOnce()).enviar(eq("socia@exemplo.test"), anyString(), corpo.capture());
		String token = corpo.getValue().replaceAll("(?s).*moderar\\?t=([A-Za-z0-9_-]+).*", "$1");

		assertThat(servico.porToken(token)).isPresent(); // abrir o link nao decide nada
		assertThat(servico.aprovados()).hasSize((int) antes);

		assertThat(servico.decidirPorToken(token, true)).contains("APROVADO");
		assertThat(servico.aprovados()).hasSize((int) antes + 1);
		assertThat(servico.aprovados().get(0).nome()).isEqualTo("Maria Teste");
		// a primeira decisao vale: a segunda (de outra socia) nao desfaz
		assertThat(servico.decidirPorToken(token, false)).contains("APROVADO");
		assertThat(servico.aprovados()).hasSize((int) antes + 1);
	}

	@Test
	void rejeitadoNuncaPublicaETokenInvalidoNaoDecide() {
		long antes = servico.aprovados().size();
		servico.receber("Joao Teste", "joao@exemplo.test", "Comentario que sera rejeitado pela socia.");
		Long id = jdbc.queryForObject("select max(id) from comentario_site", Long.class);
		assertThat(servico.decidirPorToken("token-que-nao-existe-de-jeito-nenhum", true)).isEmpty();
		assertThat(servico.decidirNoSistema(null, id, false)).isEqualTo("REJEITADO");
		assertThat(servico.aprovados()).hasSize((int) antes);
		assertThat(jdbc.queryForObject("select token_hash from comentario_site where id = ?", String.class, id)).hasSize(64);
	}

	@Test
	void validaEntradaEOsDoisDepoimentosAntigosFicaramAprovados() {
		assertThatThrownBy(() -> servico.receber("A", "x@exemplo.test", "mensagem longa o bastante"))
				.isInstanceOf(ComentarioService.ComentarioInvalidoException.class);
		assertThatThrownBy(() -> servico.receber("Fulano", "nao-e-email", "mensagem longa o bastante"))
				.isInstanceOf(ComentarioService.ComentarioInvalidoException.class);
		assertThatThrownBy(() -> servico.receber("Fulano", "f@exemplo.test", "curta"))
				.isInstanceOf(ComentarioService.ComentarioInvalidoException.class);
		assertThat(servico.aprovados()).extracting(ComentarioService.Publico::nome).contains("Raul", "Pai de cliente");
	}
}
