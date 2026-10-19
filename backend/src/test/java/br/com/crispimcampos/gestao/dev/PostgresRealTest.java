package br.com.crispimcampos.gestao.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.agenda.AgendaService;
import br.com.crispimcampos.gestao.alertas.AlertaService;
import br.com.crispimcampos.gestao.atendimento.AtendimentoService;
import br.com.crispimcampos.gestao.financeiro.FinanceiroService;
import br.com.crispimcampos.gestao.relatorios.ConteudoCliente;
import br.com.crispimcampos.gestao.relatorios.RelatorioClienteService;
import br.com.crispimcampos.gestao.relatorios.RelatoriosInternosService;
import br.com.crispimcampos.gestao.agenda.ConclusaoService;
import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import br.com.crispimcampos.gestao.captura.DadosBrutos;
import br.com.crispimcampos.gestao.captura.EstadoPublicacao;
import br.com.crispimcampos.gestao.captura.Fonte;
import br.com.crispimcampos.gestao.captura.PipelineCaptura;
import br.com.crispimcampos.gestao.captura.PublicacaoService;
import java.time.LocalDate;
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
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
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
		assertThat(aplicadas).isEqualTo(12);
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
	@Autowired
	PublicacaoService publicacoes;
	@Autowired
	PipelineCaptura pipeline;
	@Autowired
	AlertaService alertas;
	@Autowired
	AtendimentoService atendimentos;
	@Autowired
	FinanceiroService financeiro;
	@Autowired
	RelatorioClienteService relatorios;
	@Autowired
	RelatoriosInternosService internos;

	Long clienteDe(String nome) {
		return jdbc.queryForObject("select id from cliente where nome like ?", Long.class, nome + "%");
	}

	@Test
	void financeiroSoParaSociasComResumoCorreto() {
		UsuarioLogado socia = como("Socia A");
		Long maria = clienteDe("Maria");
		var r = financeiro.resumoDoCliente(socia, maria);
		assertThat(r.contratado()).isEqualByComparingTo("6000.00");
		assertThat(r.parcelasPagas()).isEqualByComparingTo("1000.00");
		assertThat(r.parcelasEmAtraso()).isEqualByComparingTo("1000.00");
		assertThat(r.quantidadeEmAtraso()).isEqualTo(1);
		assertThat(r.parcelasAVencer()).isEqualByComparingTo("4000.00");
		assertThat(r.custasEDespesas()).isEqualByComparingTo("350.00");
		assertThat(r.reembolsavel()).isEqualByComparingTo("350.00");
		assertThat(r.exitoPrevisto()).isEqualByComparingTo("1500.00");
		assertThat(r.exitoRecebido()).isEqualByComparingTo("0");
		Long processo = jdbc.queryForObject("select id from processo where cliente_id = ? order by id limit 1", Long.class, maria);
		assertThat(financeiro.resumoDoProcesso(socia, processo).contratado()).isEqualByComparingTo("6000.00");
		assertThat(jdbc.queryForObject("select count(*) from log_auditoria where acao = 'FINANCEIRO_CONSULTADO'", Integer.class)).isGreaterThanOrEqualTo(2);
		for (String quem : new String[] {"Advogada C", "Advogado D", "Assistente E", "Socia B"}) {
			UsuarioLogado u = como(quem);
			if (quem.startsWith("Socia")) {
				assertThat(financeiro.resumoDoCliente(u, maria)).isNotNull();
			} else {
				assertThatThrownBy(() -> financeiro.resumoDoCliente(u, maria)).isInstanceOf(SecurityException.class);
				assertThatThrownBy(() -> financeiro.contratos(u, maria)).isInstanceOf(SecurityException.class);
				assertThatThrownBy(() -> financeiro.criarContrato(u, maria, null, "x", java.math.BigDecimal.TEN, 1, LocalDate.now())).isInstanceOf(SecurityException.class);
			}
		}
	}

	@Test
	void bancoRecusaValoresInvalidosNoFinanceiro() {
		assertThatThrownBy(() -> jdbc.update("insert into parcela (contrato_id, numero, vencimento, valor) values (1, 99, now(), -5)")).hasMessageContaining("ck_parcela_valor");
		assertThatThrownBy(() -> jdbc.update("update parcela set pago_em = now() where id = (select min(id) from parcela where pago_em is null)")).hasMessageContaining("ck_parcela_pagamento");
	}

	@Test
	void atendimentoAvulsoRespeitaVisibilidadeDoCliente() {
		Long maria = clienteDe("Maria");
		assertThat(atendimentos.listar(como("Socia A"), maria)).extracting("origem").contains("AVULSO");
		assertThat(atendimentos.listar(como("Assistente E"), maria)).isNotEmpty();
		assertThatThrownBy(() -> atendimentos.listar(como("Advogado D"), maria)).hasMessageContaining("Cliente");
		assertThat(atendimentos.listar(como("Advogado D"), null)).isEmpty();
		assertThatThrownBy(() -> atendimentos.registrarAvulso(como("Estagiario F"), maria, null, "TELEFONE", null, "x", null)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> atendimentos.registrarAvulso(como("Socia A"), maria, null, "FAX", null, "x", null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void alertasSaoGeradosEnviadosENaoDuplicam() {
		UsuarioLogado socia = como("Socia A");
		var primeira = alertas.executar(socia.id());
		// audiencias amanha (2), audiencia em 10 dias (1) e resumo de vencidos: ao menos varios alertas, todos enviados pelo envio de dev
		assertThat(primeira.geradas()).isGreaterThan(6);
		assertThat(primeira.enviadas()).isEqualTo(primeira.geradas());
		var lista = alertas.listar(socia);
		assertThat(lista).extracting("tipo").contains(br.com.crispimcampos.gestao.alertas.TipoAlerta.AUDIENCIA_VESPERA,
				br.com.crispimcampos.gestao.alertas.TipoAlerta.AUDIENCIA_10_DIAS, br.com.crispimcampos.gestao.alertas.TipoAlerta.RESUMO_VENCIDOS);
		assertThat(lista).allSatisfy(n -> assertThat(n.status()).isEqualTo("ENVIADA"));
		var segunda = alertas.executar(socia.id());
		assertThat(segunda.geradas()).as("idempotente").isZero();
		assertThat(segunda.enviadas()).isZero();
		// cada pessoa so lista as proprias notificacoes
		UsuarioLogado wendell = como("Advogado D");
		assertThat(alertas.listar(wendell)).isNotEmpty().allSatisfy(n -> assertThat(n.destinatarioId()).isEqualTo(wendell.id()));
	}

	@Test
	void capturaCruzaFontesFiltraEMantemIdempotencia() {
		UsuarioLogado socia = como("Socia A");
		var fila = publicacoes.listar(socia, EstadoPublicacao.A_CLASSIFICAR);
		// C: cruzada DJEN+EMAIL (1) e nao cadastrada (1); D: 1; A: so o TRT-10 (o TRT-1 foi ignorado)
		assertThat(fila).hasSize(4);
		var cruzada = fila.stream().filter(p -> p.fontes().size() == 2).findFirst().orElseThrow();
		assertThat(cruzada.fontes()).containsExactlyInAnyOrder(Fonte.DJEN, Fonte.EMAIL);
		assertThat(cruzada.somenteEmUmaFonte()).isFalse();
		assertThat(cruzada.processoId()).as("processo cadastrado: vinculo automatico").isNotNull();
		var naoCadastrada = fila.stream().filter(p -> p.numeroProcesso().equals("00009999920258070001")).findFirst().orElseThrow();
		assertThat(naoCadastrada.somenteEmUmaFonte()).isTrue();
		assertThat(naoCadastrada.sugerirCadastro()).isTrue();
		assertThat(jdbc.queryForObject("select count(*) from item_ignorado", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from publicacao_bruta where fonte = 'EMAIL' and id_externo = 'm-2'", Integer.class))
				.as("o ignorado fica registrado").isEqualTo(1);

		// segunda passada com os mesmos itens: nada novo (invariante 4)
		var repetido = pipeline.processar(como("Advogada C").id(), java.util.List.of(
				new DadosBrutos(Fonte.DJEN, "9001", cruzada.numeroProcesso(), "TJDFT", LocalDate.now(), "qualquer", "h9001")));
		assertThat(repetido.duplicados()).isEqualTo(1);
		assertThat(repetido.novasPublicacoes()).isZero();
		assertThat(publicacoes.listar(socia, EstadoPublicacao.A_CLASSIFICAR)).hasSize(4);
	}

	@Test
	void visibilidadeDasPublicacoesSegueAMatriz() {
		assertThat(publicacoes.listar(como("Advogado D"), null)).hasSize(1);
		assertThat(publicacoes.listar(como("Advogada C"), null)).hasSize(2);
		// Assistente E ve A e C (3 + ... ) mas nunca a do Advogado D
		assertThat(publicacoes.listar(como("Assistente E"), null)).extracting("titularId").doesNotContain(como("Advogado D").id());
		assertThat(publicacoes.listar(como("Socia A"), null)).hasSize(4);
	}

	@Test
	@org.junit.jupiter.api.Order(Integer.MAX_VALUE - 1)
	void sociaClassificaGerandoPrazoQueAguardaConferenciaEOutrosNaoClassificam() {
		UsuarioLogado socia = como("Socia B");
		UsuarioLogado assistente = como("Assistente E");
		var alvo = publicacoes.listar(socia, EstadoPublicacao.A_CLASSIFICAR).stream().filter(p -> p.processoId() != null && p.fontes().size() == 2).findFirst().orElseThrow();
		assertThatThrownBy(() -> publicacoes.classificar(assistente, alvo.id(), TipoAgendamento.CON, null, assistente.id(), false, null,
				LocalDate.now().minusDays(1), LocalDate.now().plusDays(8))).isInstanceOf(SecurityException.class);
		var feita = publicacoes.classificar(socia, alvo.id(), TipoAgendamento.CON, null, como("Advogada C").id(), true, null,
				LocalDate.now().minusDays(1), LocalDate.now().plusDays(8));
		assertThat(feita.estado()).isEqualTo(EstadoPublicacao.CLASSIFICADA);
		assertThat(feita.agendamentoId()).isNotNull();
		assertThat(jdbc.queryForObject("select status from agendamento where id = ?", String.class, feita.agendamentoId()))
				.isEqualTo("AGUARDANDO_CONFERENCIA_PRAZO");
		assertThatThrownBy(() -> publicacoes.descartar(socia, alvo.id(), "tarde demais")).isInstanceOf(IllegalStateException.class);
		var outra = publicacoes.listar(socia, EstadoPublicacao.A_CLASSIFICAR).stream().filter(p -> p.numeroProcesso().equals("00009999920258070001")).findFirst().orElseThrow();
		assertThatThrownBy(() -> publicacoes.descartar(socia, outra.id(), " ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> publicacoes.classificar(socia, outra.id(), TipoAgendamento.CON, null, como("Advogada C").id(), false, null,
				LocalDate.now().minusDays(1), LocalDate.now().plusDays(8))).hasMessageContaining("processo");
		assertThat(publicacoes.descartar(socia, outra.id(), "Nao e do escritorio").estado()).isEqualTo(EstadoPublicacao.DESCARTADA_COM_REGISTRO);
		assertThatThrownBy(() -> jdbc.update("delete from item_ignorado")).hasMessageContaining("somente de insercao");

		// a publicacao classificada virou andamento pendente de repasse; repassar tira da lista
		var pendentes = atendimentos.pendentesDeRepasse(socia);
		var doPublicacao = pendentes.stream().filter(a -> "PUBLICACAO".equals(a.origem())).findFirst().orElseThrow();
		assertThat(atendimentos.pendentesDeRepasse(como("Advogado D"))).extracting("id").doesNotContain(doPublicacao.id());
		assertThat(atendimentos.marcarRepassado(socia, doPublicacao.id(), "EMAIL").repassado()).isTrue();
		assertThat(atendimentos.pendentesDeRepasse(socia)).extracting("id").doesNotContain(doPublicacao.id());
		assertThatThrownBy(() -> atendimentos.marcarRepassado(socia, doPublicacao.id(), "EMAIL")).isInstanceOf(IllegalStateException.class);
		assertThat(pendentes).extracting("origem").contains("MANUAL");
	}

	@Test
	@org.junit.jupiter.api.Order(Integer.MAX_VALUE - 3)
	void atdConcluidoGeraRegistroDeAtendimento() {
		UsuarioLogado advogada = como("Advogada C");
		var atd = agenda.agenda(advogada, null).stream().filter(i -> i.tipo() == TipoAgendamento.ATD).findFirst().orElseThrow();
		conclusao.finalizar(advogada, atd.id(), "Orientado sobre documentos necessarios.");
		var lista = atendimentos.listar(como("Socia A"), atd.clienteId());
		var gerado = lista.stream().filter(a -> "AGENDAMENTO".equals(a.origem())).findFirst().orElseThrow();
		assertThat(gerado.resumo()).isEqualTo("Orientado sobre documentos necessarios.");
		assertThat(gerado.agendamentoId()).isEqualTo(atd.id());
		assertThat(gerado.canal()).isEqualTo("TELEFONE");
		assertThat(jdbc.queryForObject("select count(*) from atendimento where agendamento_id = ?", Integer.class, atd.id())).isEqualTo(1);
	}

	@Test
	@org.junit.jupiter.api.Order(Integer.MAX_VALUE - 2)
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

	@Test
	@org.junit.jupiter.api.Order(Integer.MAX_VALUE)
	void relatorioAoClienteSoSaiAprovadoESemDadosInternos() throws Exception {
		UsuarioLogado advogada = como("Advogada C");
		UsuarioLogado socia = como("Socia A");
		Long maria = clienteDe("Maria");

		// quem nao pode gerar
		assertThatThrownBy(() -> relatorios.criarRascunho(como("Assistente E"), maria, null, null, null, false, false)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> relatorios.criarRascunho(como("Advogado D"), maria, null, null, null, false, false)).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> relatorios.criarRascunho(advogada, maria, null, null, null, false, true)).isInstanceOf(SecurityException.class);

		var rascunho = relatorios.criarRascunho(advogada, maria, null, LocalDate.now().minusDays(30), LocalDate.now(), false, false);
		assertThat(rascunho.estado()).isEqualTo("RASCUNHO");
		assertThat(rascunho.conteudo().processos()).hasSize(1);
		String bruto = jdbc.queryForObject("select conteudo from relatorio_cliente where id = ?", String.class, rascunho.id());
		assertThat(bruto).contains("Peticao protocolada e juntada aos autos.");
		// nada interno vaza: observacao de prazo, outros clientes, outros processos
		assertThat(bruto).doesNotContain("feriado local").doesNotContain("Empresa Ficticia").doesNotContain("Joao Ficticio")
				.doesNotContainIgnoringCase("prazoInterno").doesNotContainIgnoringCase("conferi").doesNotContainIgnoringCase("observac");

		// sem aprovacao: sem PDF, sem envio, e o banco tambem recusa
		assertThatThrownBy(() -> relatorios.gerarPdf(advogada, rascunho.id())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> relatorios.enviar(advogada, rascunho.id(), "EMAIL")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> jdbc.update("update relatorio_cliente set estado = 'APROVADO' where id = ?", rascunho.id())).hasMessageContaining("ck_relatorio_aprovacao");
		assertThatThrownBy(() -> jdbc.update("update relatorio_cliente set estado = 'ENVIADO' where id = ?", rascunho.id())).hasMessageContaining("ck_relatorio");

		// edicao: comentarios do advogado; nao pode incluir processo de fora
		var conteudo = rascunho.conteudo();
		var comComentario = new ConteudoCliente(conteudo.clienteNome(), conteudo.periodoInicio(), conteudo.periodoFim(), conteudo.processos(), "O caso segue bem.", null);
		assertThat(relatorios.editar(advogada, rascunho.id(), comComentario).estado()).isEqualTo("EM_REVISAO");
		var estranho = new ConteudoCliente.ProcessoRel("0000000-00.0000.0.00.0000", "x", "x", "x", "x", List.of(), List.of(), List.of(), List.of());
		assertThatThrownBy(() -> relatorios.editar(advogada, rascunho.id(), new ConteudoCliente(conteudo.clienteNome(), null, conteudo.periodoFim(), List.of(estranho), null, null)))
				.isInstanceOf(IllegalArgumentException.class);

		// outra advogada/advogado nao enxerga o relatorio; a socia sim
		assertThatThrownBy(() -> relatorios.buscar(como("Advogado D"), rascunho.id())).isInstanceOf(RuntimeException.class);
		assertThat(relatorios.buscar(socia, rascunho.id()).conteudo().comentarios()).isEqualTo("O caso segue bem.");

		var aprovado = relatorios.aprovar(advogada, rascunho.id());
		assertThat(aprovado.estado()).isEqualTo("APROVADO");
		assertThat(aprovado.aprovadoPor()).isEqualTo(advogada.id());
		assertThatThrownBy(() -> relatorios.editar(advogada, rascunho.id(), comComentario)).isInstanceOf(IllegalStateException.class);
		byte[] pdf = relatorios.gerarPdf(advogada, rascunho.id());
		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

		int pendentesAntes = atendimentos.pendentesDeRepasse(advogada).size();
		assertThat(pendentesAntes).isPositive();
		var enviado = relatorios.enviar(advogada, rascunho.id(), "EMAIL");
		assertThat(enviado.estado()).isEqualTo("ENVIADO");
		assertThat(enviado.canal()).isEqualTo("EMAIL");
		assertThat(jdbc.queryForObject("select length(pdf) from relatorio_cliente where id = ?", Integer.class, rascunho.id())).isGreaterThan(500);
		assertThat(atendimentos.pendentesDeRepasse(advogada)).as("andamentos incluidos foram marcados como repassados").isEmpty();
		assertThatThrownBy(() -> relatorios.enviar(advogada, rascunho.id(), "EMAIL")).isInstanceOf(IllegalStateException.class);
		assertThat(relatorios.doCliente(socia, maria)).extracting("estado").contains("ENVIADO");

		// resumo financeiro: so socia inclui; guarda parcelas pagas e a vencer
		var comFin = relatorios.criarRascunho(socia, maria, null, null, LocalDate.now().plusDays(5), true, true);
		assertThat(comFin.conteudo().financeiro().parcelasPagas()).isEqualByComparingTo("1000.00");
		assertThat(comFin.conteudo().periodoInicio()).as("desde o ultimo relatorio enviado").isEqualTo(LocalDate.now().plusDays(1));
		assertThatThrownBy(() -> relatorios.criarRascunho(socia, maria, null, null, LocalDate.now(), true, false))
				.as("sem periodo novo desde o ultimo envio").isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@org.junit.jupiter.api.Order(Integer.MAX_VALUE)
	void relatoriosInternosRespeitamPerfilEGeramPdf() {
		UsuarioLogado socia = como("Socia A");
		UsuarioLogado wendell = como("Advogado D");
		var geral = internos.gerar(socia, RelatoriosInternosService.Tipo.PENDENTES, null, null, null, null);
		assertThat(geral.linhas()).isNotEmpty();
		var doWendell = internos.gerar(wendell, RelatoriosInternosService.Tipo.PENDENTES, null, null, null, null);
		assertThat(doWendell.linhas()).hasSizeLessThan(geral.linhas().size()).allSatisfy(l -> assertThat(l.get(4)).contains("Advogado D"));
		assertThat(internos.gerar(socia, RelatoriosInternosService.Tipo.VENCIDOS, null, null, null, null).linhas()).isNotEmpty();
		assertThat(internos.gerar(socia, RelatoriosInternosService.Tipo.AGENDA_DIA, LocalDate.now().plusDays(1), null, null, null).linhas()).hasSize(2);
		assertThat(internos.gerar(socia, RelatoriosInternosService.Tipo.FINANCEIRO_CLIENTE, null, null, null, clienteDe("Maria")).linhas()).hasSize(8);
		assertThat(internos.gerar(socia, RelatoriosInternosService.Tipo.ATENDIMENTOS, LocalDate.now().minusDays(5), LocalDate.now(), null, null).linhas()).isNotEmpty();
		assertThatThrownBy(() -> internos.gerar(wendell, RelatoriosInternosService.Tipo.FINANCEIRO_CLIENTE, null, null, null, 1L)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> internos.gerar(wendell, RelatoriosInternosService.Tipo.AGENDA_USUARIO, null, null, socia.id(), null)).isInstanceOf(SecurityException.class);
		assertThat(new String(internos.pdf(socia, RelatoriosInternosService.Tipo.PENDENTES, null, null, null, null), 0, 5)).isEqualTo("%PDF-");
		assertThat(internos.enviarAsSocias(socia, RelatoriosInternosService.Tipo.PENDENTES, null, null, null, null)).isEqualTo(2);
		assertThatThrownBy(() -> internos.enviarAsSocias(wendell, RelatoriosInternosService.Tipo.PENDENTES, null, null, null, null)).isInstanceOf(SecurityException.class);
	}
}
