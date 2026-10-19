package br.com.crispimcampos.gestao.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import br.com.crispimcampos.gestao.sistema.ConfiguracaoService;
import br.com.crispimcampos.gestao.sistema.ProntidaoService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Acesso mestre em Postgres real: criacao pelo bootstrap, ROLE_MESTRE, visao total, protecao e configuracao. */
@SpringBootTest(properties = {"app.bootstrap.mestre.nome=Mestre Teste", "app.bootstrap.mestre.email=Mestre@Exemplo.test",
		"app.bootstrap.mestre.senha=senha-mestre-teste-1"})
class PostgresMestreTest {

	static final EmbeddedPostgres PG = iniciar();

	static EmbeddedPostgres iniciar() {
		try {
			return EmbeddedPostgres.builder().setDataDirectory(Files.createTempDirectory("pg-mestre")).start();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void banco(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
		r.add("spring.datasource.username", () -> "postgres");
		r.add("spring.datasource.password", () -> "postgres");
		r.add("STORAGE_DIR", () -> "target/teste-storage-mestre");
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
	UserDetailsService detalhes;
	@Autowired
	PasswordEncoder encoder;
	@Autowired
	ConfiguracaoService configuracao;
	@Autowired
	ProntidaoService prontidao;

	@Test
	void bootstrapCriaOMestreComoSociaComRoleMestre() {
		var u = (UsuarioLogado) detalhes.loadUserByUsername("mestre@exemplo.test");
		assertThat(u.mestre()).isTrue();
		assertThat(u.perfil()).isEqualTo(Perfil.SOCIA);
		assertThat(u.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("ROLE_SOCIA", "ROLE_MESTRE");
		assertThat(encoder.matches("senha-mestre-teste-1", u.getPassword())).isTrue();
		assertThat(usuarios.buscar(u.id()).destinoAlertas()).isNull();
	}

	@Test
	void mestreVeTudoMesmoSemRegrasNaMatrizEProtegeAConta() {
		var m = (UsuarioLogado) detalhes.loadUserByUsername("mestre@exemplo.test");
		UsuarioDto adv = usuarios.criar(m.id(), "Adv Teste", "adv@exemplo.test", "senha-adv-teste-1", Perfil.ADVOGADO, null);
		UsuarioDto socia = usuarios.criar(m.id(), "Socia Teste", "socia@exemplo.test", "senha-socia-teste-1", Perfil.SOCIA, null);
		jdbc.update("delete from regra_visibilidade where observador_id = ?", m.id());
		assertThat(visibilidade.titularesVisiveis(m, Escopo.PROCESSO)).contains(m.id(), adv.id(), socia.id());
		assertThatThrownBy(() -> usuarios.redefinirSenha(socia.id(), m.id(), "outra-senha-qualquer-1"))
				.hasMessageContaining("acesso mestre");
		assertThatThrownBy(() -> jdbc.update("update usuario set mestre = true where id = ?", adv.id()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void configuracaoDoEscritorioPersisteEAlimentaAProntidao() {
		var m = (UsuarioLogado) detalhes.loadUserByUsername("mestre@exemplo.test");
		assertThat(configuracao.todas().get(ConfiguracaoService.ESCRITORIO_NOME)).isEqualTo("Crispim & Campos Advocacia");
		configuracao.salvar(m.id(), java.util.Map.of(ConfiguracaoService.ESCRITORIO_ENDERECO, " Rua X, 1 ",
				ConfiguracaoService.ESCRITORIO_CONTATO, "(61) 0000-0000"));
		assertThat(configuracao.valor(ConfiguracaoService.ESCRITORIO_ENDERECO, "")).isEqualTo("Rua X, 1");
		assertThatThrownBy(() -> configuracao.salvar(m.id(), java.util.Map.of("chave.inventada", "x")))
				.isInstanceOf(IllegalArgumentException.class);
		var itens = prontidao.diagnosticar();
		assertThat(itens).extracting(ProntidaoService.Item::id).contains("escritorio", "oabs", "smtp", "rotinas", "2fa");
		assertThat(itens.stream().filter(i -> i.id().equals("escritorio")).findFirst().orElseThrow().situacao())
				.isEqualTo(ProntidaoService.Situacao.OK);
	}
}
