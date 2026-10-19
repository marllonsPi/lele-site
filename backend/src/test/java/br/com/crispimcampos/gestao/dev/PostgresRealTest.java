package br.com.crispimcampos.gestao.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.agenda.AgendaService;
import br.com.crispimcampos.gestao.agenda.ConclusaoService;
import br.com.crispimcampos.gestao.agenda.PrazoService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Contexto completo contra Postgres real (sem Docker): migracoes V1..V6, validacao do mapeamento JPA, constraints
 * do banco e o fluxo do DevSeed passando pelas regras de dominio.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PostgresRealTest {

	static final EmbeddedPostgres PG = iniciar();

	static EmbeddedPostgres iniciar() {
		try {
			return EmbeddedPostgres.builder().setDataDirectory(Files.createTempDirectory("pg-teste")).start();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void banco(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
		r.add("spring.datasource.username", () -> "postgres");
		r.add("spring.datasource.password", () -> "postgres");
		r.add("STORAGE_DIR", () -> "target/teste-storage");
	}

	@AfterAll
	static void encerra() throws IOException {
		PG.close();
	}

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	UsuarioService usuarios;
	@Autowired
	Visibilidade visibilidade;
	@Autowired
	CadastroService cadastros;
	@Autowired
	PrazoService prazos;
	@Autowired
	AgendaService agenda;
	@Autowired
	DataSource dataSource;

	UsuarioLogado como(String nome) {
		UsuarioDto u = usuarios.listar().stream().filter(x -> x.nome().startsWith(nome)).findFirst().orElseThrow();
		return new UsuarioLogado(u.id(), u.nome(), u.email(), "x", u.perfil(), true);
	}

	@Test
	void migracoesAplicadasEMapeamentoValido() {
		Integer aplicadas = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
		assertThat(aplicadas).isEqualTo(7);
	}

	@Test
	void seedCriouAEquipeComAMatrizDaSpec() {
		assertThat(usuarios.listar()).hasSize(6);
		UsuarioLogado luana = como("Assistente E");
		UsuarioLogado wendell = como("Advogado D");
		UsuarioLogado andressa = como("Advogada C");
		UsuarioLogado socia = como("Socia A");
		var titularesLuana = visibilidade.titularesVisiveis(luana, Escopo.AGENDAMENTO);
		assertThat(titularesLuana).contains(socia.id(), andressa.id()).doesNotContain(wendell.id());
		assertThat(visibilidade.titularesVisiveis(wendell, Escopo.PROCESSO)).containsExactly(wendell.id());
		assertThat(visibilidade.titularesVisiveis(andressa, Escopo.PROCESSO)).containsExactly(andressa.id());
	}

	@Test
	void processosFiltradosPorVisibilidadeNoBanco() {
		assertThat(cadastros.listarProcessos(como("Advogado D"), null)).hasSize(1);
		assertThat(cadastros.listarProcessos(como("Advogada C"), null)).hasSize(1);
		assertThat(cadastros.listarProcessos(como("Socia A"), null)).hasSize(4);
		// Assistente E nao ve o processo do Advogado D (3 de 4)
		assertThat(cadastros.listarProcessos(como("Assistente E"), null)).hasSize(3);
	}

	@Test
	void prazosEAgendaNoBanco() {
		var prazosSocia = prazos.listar(como("Socia A"));
		assertThat(prazosSocia).hasSizeGreaterThanOrEqualTo(4);
		assertThat(prazosSocia).allSatisfy(p -> assertThat(p.prazoInterno()).isBeforeOrEqualTo(p.prazoFatal()));
		assertThat(prazos.listar(como("Advogado D"))).allSatisfy(p -> assertThat(p.responsavelId()).isEqualTo(como("Advogado D").id()));
		assertThat(agenda.agenda(como("Advogada C"), null)).isNotEmpty();
	}

	@Autowired
	ConclusaoService conclusao;

	@Test
	void fluxoCompletoDoEstagiarioAteAConferenciaDaSocia() {
		UsuarioLogado estagiario = como("Estagiario F");
		UsuarioLogado supervisora = como("Advogada C");
		UsuarioLogado socia = como("Socia A");
		Long id = prazos.listar(estagiario).stream().filter(p -> p.status().name().equals("PENDENTE")).findFirst().orElseThrow().id();
		assertThat(agenda.agenda(estagiario, null)).extracting("id").contains(id);

		assertThat(conclusao.finalizar(estagiario, id).status().name()).isEqualTo("EM_CONFERENCIA_SUPERVISOR");
		assertThat(conclusao.pendentesDeConferencia(supervisora)).extracting("id").contains(id);
		assertThat(conclusao.pendentesDeConferencia(socia)).extracting("id").doesNotContain(id);

		assertThat(conclusao.devolverSupervisor(supervisora, id, "Falta o documento").status().name()).isEqualTo("DEVOLVIDO");
		assertThat(agenda.agenda(estagiario, null)).extracting("id").contains(id);
		conclusao.finalizar(estagiario, id);
		assertThat(conclusao.aprovarSupervisor(supervisora, id).status().name()).isEqualTo("EM_CONFERENCIA_SOCIA");
		assertThat(conclusao.pendentesDeConferencia(socia)).extracting("id").contains(id);
		assertThat(conclusao.conferirSocia(socia, id).status().name()).isEqualTo("CONFERIDO");
		assertThat(conclusao.historico(socia, id)).extracting("resultado").containsExactly("DEVOLVIDO", "CONFERIDO", "CONFERIDO");
		assertThatThrownBy(() -> jdbc.update("delete from conferencia")).hasMessageContaining("somente de insercao");
	}

	@Test
	void bancoRecusaConferenciaPeloMesmoUsuario() {
		Long id = jdbc.queryForObject("select id from agendamento where status = 'AGUARDANDO_CONFERENCIA_PRAZO' limit 1", Long.class);
		Long lancador = jdbc.queryForObject("select lancado_por from agendamento where id = ?", Long.class, id);
		assertThatThrownBy(() -> jdbc.update("update agendamento set conferido_por = ?, conferido_em = now(), status = 'PENDENTE' where id = ?", lancador, id))
				.hasMessageContaining("ck_agendamento_dupla_conferencia");
	}

	@Test
	void bancoImpedeAlterarAuditoriaEObservacoes() {
		assertThatThrownBy(() -> jdbc.update("update log_auditoria set acao = 'X'")).hasMessageContaining("somente de insercao");
		assertThatThrownBy(() -> jdbc.update("delete from observacao_agendamento")).hasMessageContaining("somente de insercao");
	}

	@Test
	void bancoImpedeAudienciaSemProcessoEPrazoSemDatas() {
		Long u = como("Socia A").id();
		assertThatThrownBy(() -> jdbc.update("insert into agendamento (tipo, titular_id, responsavel_id, criado_por, status, inicio_em, duracao_min, modalidade) "
				+ "values ('AUD', ?, ?, ?, 'PENDENTE', now(), 60, 'ONLINE')", u, u, u)).hasMessageContaining("ck_agendamento_processo");
		Long processo = jdbc.queryForObject("select id from processo limit 1", Long.class);
		assertThatThrownBy(() -> jdbc.update("insert into agendamento (processo_id, tipo, titular_id, responsavel_id, criado_por, status) "
				+ "values (?, 'CON', ?, ?, ?, 'AGUARDANDO_CONFERENCIA_PRAZO')", processo, u, u, u)).hasMessageContaining("ck_agendamento_datas");
	}

	@Test
	void calendarioSeedEUnicidadesDoCadastro() {
		assertThat(jdbc.queryForObject("select count(*) from feriado where escopo = 'NACIONAL'", Integer.class)).isGreaterThan(20);
		String numero = jdbc.queryForObject("select numero from processo limit 1", String.class);
		Long cliente = jdbc.queryForObject("select id from cliente limit 1", Long.class);
		Long adv = como("Advogada C").id();
		assertThatThrownBy(() -> jdbc.update("insert into processo (numero, tribunal, cliente_id, advogado_id) values (?, 'X', ?, ?)", numero, cliente, adv))
				.hasMessageContaining("uq_processo_numero");
	}

	@Test
	void perfisSemSociaNaoPassamDeDirecionamentoDeDados() throws SQLException {
		assertThat(dataSource.getConnection().isValid(2)).isTrue();
		assertThat(usuarios.listar()).extracting(UsuarioDto::perfil).contains(Perfil.SOCIA, Perfil.ADVOGADO, Perfil.ASSISTENTE);
		assertThat(List.of(Perfil.values())).hasSize(4);
	}
}
