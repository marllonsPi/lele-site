package br.com.crispimcampos.gestao.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Acao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Decisao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Tipo;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Importacao da planilha ponta a ponta em Postgres real, com planilha ficticia: previa, pendencias, confirmacao, idempotencia, conflito. */
@SpringBootTest(properties = {"app.bootstrap.mestre.nome=Socia Teste", "app.bootstrap.mestre.email=socia@exemplo.test",
		"app.bootstrap.mestre.senha=senha-socia-teste-1"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportacaoPostgresTest {

	static final EmbeddedPostgres PG = iniciar();

	static EmbeddedPostgres iniciar() {
		try {
			return EmbeddedPostgres.builder().setDataDirectory(Files.createTempDirectory("pg-importacao")).start();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void banco(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
		r.add("spring.datasource.username", () -> "postgres");
		r.add("spring.datasource.password", () -> "postgres");
		r.add("STORAGE_DIR", () -> "target/teste-storage-importacao");
	}

	@AfterAll
	static void encerra() throws IOException {
		PG.close();
	}

	@Autowired
	ImportacaoService servico;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	UserDetailsService detalhes;

	UsuarioLogado socia() {
		return (UsuarioLogado) detalhes.loadUserByUsername("socia@exemplo.test");
	}

	static ItemPrevia achar(ImportacaoDto d, Tipo tipo, String trechoRotulo) {
		return d.itens().stream().filter(i -> i.tipo() == tipo && i.rotulo().contains(trechoRotulo)).findFirst().orElseThrow();
	}

	@Test
	@Order(1)
	void somenteSociasImportam() {
		var advogado = new UsuarioLogado(99L, "Adv", "adv@exemplo.test", "x", Perfil.ADVOGADO, true);
		assertThatThrownBy(() -> servico.gerarPreviaPlanilha(advogado, "p.xlsx", XlsxFicticio.planilhaModelo(), null)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> servico.listar(advogado)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> servico.gerarPreviaPlanilha(socia(), "x.xlsx", "nao e planilha".getBytes(), null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@Order(2)
	void previaMostraDivergenciasENadaEGravadoAteConfirmar() {
		ImportacaoDto d = servico.gerarPreviaPlanilha(socia(), "planilha-ficticia.xlsx", XlsxFicticio.planilhaModelo(), null);
		assertThat(d.status()).isEqualTo("PREVIA");
		assertThat(jdbc.queryForObject("select count(*) from cliente", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from parcela", Integer.class)).isZero();
		assertThat(d.contagens().get("CLIENTE")).containsEntry("CRIAR", 4L);
		assertThat(d.contagens().get("CONTRATO").get("CRIAR")).isEqualTo(4L); // 3 da aba Processos + 1 avulso (Daniel)
		assertThat(d.contagens().get("PARCELA").get("CRIAR")).isEqualTo(6L);
		assertThat(d.contagens().get("DESPESA").get("CRIAR")).isEqualTo(1L);
		assertThat(d.comDivergencia()).isPositive();
		assertThat(achar(d, Tipo.CLIENTE, "ANA EXEMPLO").divergencias()).anyMatch(x -> x.contains("CPF/CNPJ"));
		assertThat(achar(d, Tipo.PARCELA, "DANIEL").divergencias()).anyMatch(x -> x.contains("sem contrato correspondente"));
		assertThat(achar(d, Tipo.PROCESSO, "").divergencias()).anyMatch(x -> x.contains("TJDFT"));
		assertThat(d.pendencias()).anyMatch(x -> x.contains("CARLA MODELO")).anyMatch(x -> x.contains("DANIEL"));
		assertThat(d.pendencias()).anyMatch(x -> x.contains("data de vencimento"));
		assertThatThrownBy(() -> servico.confirmar(socia(), d.id())).isInstanceOf(PendenciaDeImportacaoException.class);
		assertThat(jdbc.queryForObject("select count(*) from cliente", Integer.class)).isZero();
	}

	@Test
	@Order(3)
	void confirmaDepoisDeResolverPendenciasEGravaTudo() {
		UsuarioLogado s = socia();
		Long id = servico.listar(s).get(0).id();
		ImportacaoDto d = servico.detalhe(s, id);
		servico.decidir(s, id, achar(d, Tipo.CLIENTE, "CARLA").id(), null, Map.of("areas", List.of("CIVEL")));
		servico.decidir(s, id, achar(d, Tipo.CONTRATO, "CARLA").id(), null, Map.of("area", "CIVEL"));
		servico.decidir(s, id, achar(d, Tipo.CLIENTE, "DANIEL").id(), null, Map.of("areas", List.of("CRIMINAL")));
		servico.decidir(s, id, achar(d, Tipo.PARCELA, "BRUNO").id(), null, Map.of("vencimento", "2026-04-15"));
		d = servico.detalhe(s, id);
		assertThat(d.pendencias()).isEmpty();

		ImportacaoDto fim = servico.confirmar(s, id);
		assertThat(fim.status()).isEqualTo("CONCLUIDA");
		assertThat(fim.relatorio()).containsKeys("totais", "ignorados", "erros");
		assertThat((List<?>) fim.relatorio().get("erros")).isEmpty();
		assertThat((List<?>) fim.relatorio().get("ignorados")).hasSizeGreaterThanOrEqualTo(4); // totais, extras e linha incompleta

		assertThat(jdbc.queryForObject("select count(*) from cliente", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from cliente where a_conferir and origem = 'PLANILHA'", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from cliente c where not exists (select 1 from cliente_area a where a.cliente_id = c.id)", Integer.class)).isZero();
		assertThat(jdbc.queryForList("select a.area from cliente_area a join cliente c on c.id = a.cliente_id where c.nome = 'ANA EXEMPLO DA SILVA'", String.class))
				.containsExactly("FAMILIA");
		assertThat(jdbc.queryForObject("select count(*) from processo where area = 'FAMILIA'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from contrato_honorarios where origem = 'PLANILHA' and area is not null", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from contrato_honorarios where a_conferir", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from parcela where origem = 'PLANILHA'", Integer.class)).isEqualTo(6);
		assertThat(jdbc.queryForObject("select situacao from parcela where rotulo = 'PARCELA 1/24'", String.class)).isEqualTo("PAGA");
		assertThat(jdbc.queryForObject("select situacao from parcela where rotulo like 'PARCELA 1 - %'", String.class)).isEqualTo("PARCIAL");
		assertThat(jdbc.queryForObject("select valor_pago from parcela where rotulo like 'PARCELA 1 - %'", java.math.BigDecimal.class)).isEqualByComparingTo("200.00");
		assertThat(jdbc.queryForObject("select count(*) from parcela where situacao = 'CANCELADA'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from despesa_escritorio", Integer.class)).isEqualTo(1);
		// exito 30% de 20.000 do contrato do Bruno
		assertThat(jdbc.queryForObject("select valor_previsto from honorario_variavel", java.math.BigDecimal.class)).isEqualByComparingTo("6000.00");
		assertThat(jdbc.queryForObject("select count(*) from log_auditoria where acao = 'IMPORTACAO_CONFIRMADA'", Integer.class)).isEqualTo(1);
		// a repeticao de numero "PARCELA 2/24" no mesmo contrato foi renumerada, nao perdida
		assertThat(jdbc.queryForList("select numero from parcela p join contrato_honorarios c on c.id = p.contrato_id where c.chave_origem like '%ANA EXEMPLO%' order by numero", Integer.class))
				.containsExactly(1, 2, 3);
		assertThatThrownBy(() -> servico.confirmar(s, id)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@Order(4)
	void reimportarNaoDuplicaENadaMudaSemDecisao() {
		UsuarioLogado s = socia();
		ImportacaoDto d = servico.gerarPreviaPlanilha(s, "mesma.xlsx", XlsxFicticio.planilhaModelo(), null);
		assertThat(d.contagens().values()).allSatisfy(m -> assertThat(m).doesNotContainKey("CRIAR").doesNotContainKey("CONFLITO"));
		assertThat(d.pendencias()).isEmpty();
		servico.confirmar(s, d.id());
		assertThat(jdbc.queryForObject("select count(*) from cliente", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from parcela", Integer.class)).isEqualTo(6);
		assertThat(jdbc.queryForObject("select count(*) from despesa_escritorio", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from contrato_honorarios", Integer.class)).isEqualTo(4);
	}

	@Test
	@Order(5)
	void divergenciaComODadoExistenteViraConflitoLadoALadoEExigeConfirmacao() {
		UsuarioLogado s = socia();
		// o escritorio recebeu a parcela aberta da Ana: a planilha passa a dizer PAGO; o sistema ainda tem "ABERTA"
		byte[] alterada = new XlsxFicticio()
				.aba("Contas a Receber Abril 2026", XlsxFicticio.l("ABRIL DE 2026"), XlsxFicticio.l("Cliente", "Valor", "Data de Vencimento", "Período", "Status"),
						XlsxFicticio.l("DANIEL SEM CONTRATO", 300.0, 46131.0, "ENTRADA", "PAGO"))
				.bytes();
		ImportacaoDto d = servico.gerarPreviaPlanilha(s, "alterada.xlsx", alterada, null);
		ItemPrevia p = achar(d, Tipo.PARCELA, "DANIEL");
		assertThat(p.acao()).isEqualTo(Acao.CONFLITO);
		assertThat(p.existente()).containsEntry("situacao", "ABERTA");
		assertThat(p.importado()).containsEntry("situacao", "PAGA");

		// sem decisao: confirma e o existente permanece
		ImportacaoDto d2 = servico.gerarPreviaPlanilha(s, "alterada.xlsx", alterada, null);
		servico.confirmar(s, d2.id());
		assertThat(jdbc.queryForObject("select situacao from parcela p join contrato_honorarios c on c.id = p.contrato_id where c.chave_origem like '%DANIEL%'", String.class))
				.isEqualTo("ABERTA");

		// com decisao APLICAR: atualiza
		servico.decidirEmLote(s, d.id(), Acao.CONFLITO, Tipo.PARCELA, Decisao.APLICAR);
		servico.confirmar(s, d.id());
		assertThat(jdbc.queryForObject("select situacao from parcela p join contrato_honorarios c on c.id = p.contrato_id where c.chave_origem like '%DANIEL%'", String.class))
				.isEqualTo("PAGA");
		assertThat(jdbc.queryForObject("select valor_pago from parcela p join contrato_honorarios c on c.id = p.contrato_id where c.chave_origem like '%DANIEL%'", java.math.BigDecimal.class))
				.isEqualByComparingTo("300");
	}

	@Test
	@Order(6)
	void cancelarPreviaNaoGravaNada() {
		UsuarioLogado s = socia();
		ImportacaoDto d = servico.gerarPreviaPlanilha(s, "x.xlsx", XlsxFicticio.planilhaModelo(), null);
		assertThat(servico.cancelar(s, d.id()).status()).isEqualTo("CANCELADA");
		assertThatThrownBy(() -> servico.confirmar(s, d.id())).isInstanceOf(IllegalArgumentException.class);
	}

	@Autowired
	br.com.crispimcampos.gestao.financeiro.PainelFinanceiroService painel;
	@Autowired
	br.com.crispimcampos.gestao.alertas.AlertaService alertas;
	@Autowired
	br.com.crispimcampos.gestao.contas.UsuarioService usuarios;

	@Test
	@Order(7)
	void painelFinanceiroDasSociasEAlertaDeParcelaVencida() {
		UsuarioLogado s = socia();
		var advogado = new UsuarioLogado(99L, "Adv", "adv@exemplo.test", "x", Perfil.ADVOGADO, true);
		assertThatThrownBy(() -> painel.painel(advogado, null)).isInstanceOf(SecurityException.class);

		var p = painel.painel(s, null);
		assertThat(p.quantidadeVencidas()).isPositive(); // parcelas de abril de 2026 em aberto
		assertThat(p.totalVencido()).isPositive();
		assertThat(p.vencidas()).allSatisfy(l -> {
			assertThat(l.diasAtraso()).isPositive();
			assertThat(l.cliente()).isNotBlank();
			assertThat(l.valorEmAberto()).isPositive();
		});
		assertThat(p.vencidas()).extracting(l -> l.cliente()).contains("BRUNO TESTE"); // pago pela metade: falta metade
		assertThat(p.meses()).extracting(m -> m.mes()).contains("2026-04");
		assertThat(p.meses()).anySatisfy(m -> assertThat(m.recebido()).isPositive());
		assertThat(p.porArea()).extracting(a -> a.area()).contains("FAMILIA", "TRABALHISTA");
		assertThat(p.aVencer7Dias().quantidade()).isZero();

		var soFamilia = painel.painel(s, br.com.crispimcampos.gestao.cadastros.AreaAtuacao.FAMILIA);
		assertThat(soFamilia.vencidas()).extracting(l -> l.cliente()).containsOnly("ANA EXEMPLO DA SILVA");
		assertThat(soFamilia.porArea()).extracting(a -> a.area()).containsOnly("FAMILIA");

		usuarios.criar(null, "Socia Alerta", "alerta@exemplo.test", "senha-alerta-teste-1", Perfil.SOCIA, null); // a conta mestre nao recebe alertas
		alertas.executar(null);
		assertThat(jdbc.queryForObject("select count(*) from notificacao where tipo = 'PARCELA_VENCIDA'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select corpo from notificacao where tipo = 'PARCELA_VENCIDA'", String.class))
				.contains("parcela(s)").doesNotContain("BRUNO").doesNotContain("ANA");
	}
}
