package br.com.crispimcampos.gestao.honorarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.crispimcampos.gestao.agenda.CompromissoService;
import br.com.crispimcampos.gestao.agenda.Modalidade;
import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import br.com.crispimcampos.gestao.honorarios.PropostaDtos.Fechamento;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.DadosItem;
import br.com.crispimcampos.gestao.importacao.XlsxFicticio;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipInputStream;
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

/** Tabelas de honorarios, sugestao no atendimento, fechamento, contrato/parcelas, minuta e relatorios, em Postgres real, com planilha ficticia. */
@SpringBootTest(properties = {"app.bootstrap.mestre.nome=Socia Teste", "app.bootstrap.mestre.email=socia@exemplo.test", "app.bootstrap.mestre.senha=senha-socia-teste-1"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HonorariosPostgresTest {

	static final EmbeddedPostgres PG = iniciar();

	static EmbeddedPostgres iniciar() {
		try {
			return EmbeddedPostgres.builder().setDataDirectory(Files.createTempDirectory("pg-honorarios")).start();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void banco(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
		r.add("spring.datasource.username", () -> "postgres");
		r.add("spring.datasource.password", () -> "postgres");
		r.add("STORAGE_DIR", () -> "target/teste-storage-honorarios");
	}

	@AfterAll
	static void encerra() throws IOException {
		PG.close();
	}

	@Autowired
	TabelaHonorariosService tabelas;
	@Autowired
	PropostaService propostas;
	@Autowired
	MinutaContratoService minuta;
	@Autowired
	RelatorioHonorariosService relatorios;
	@Autowired
	CompromissoService compromissos;
	@Autowired
	CadastroService cadastros;
	@Autowired
	UsuarioService usuarios;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	UserDetailsService detalhes;

	static UsuarioLogado advogado;
	static UsuarioLogado outroAdvogado;
	static UsuarioLogado assistente;
	static Long clienteId;
	static Long atdId;
	static Long propostaId;

	UsuarioLogado socia() {
		return (UsuarioLogado) detalhes.loadUserByUsername("socia@exemplo.test");
	}

	static byte[] planilha() {
		return new XlsxFicticio()
				.aba("Honorários por Área",
						XlsxFicticio.l("Crispim & Campos Advocacia — Tabela de Honorários Contratuais por Tipo"),
						XlsxFicticio.l("Baseada na Tabela de Honorários da OAB/DF"),
						XlsxFicticio.l("Área de Atuação", "Tipo de Ação / Serviço", "Percentual Sugerido", "Valor Mínimo (URH)", "Valor Mínimo (R$)", "Item Tabela OAB/DF", "Observações",
								"Valor de Tabela do Escritório (R$)", "Desconto Padrão (%)", "Valor Final Sugerido (R$)", "Forma de Parcelamento Sugerida"),
						XlsxFicticio.l("Família e Sucessões", "Divórcio consensual, sem bens, mesmo advogado", "—", 40.0, 15245.2, "40.1-a", null, 18294.24, 0.1, 16464.816,
								"1/3 na assinatura do contrato + 1/3 até a decisão de 1º grau + 1/3 ao final"),
						XlsxFicticio.l("Família e Sucessões", "Inventário e arrolamento (judicial ou administrativo)", "5% a 10% sobre o valor total dos bens", 25.0, 9528.25, "45", null,
								11433.9, 0.1, 10290.51, "1/3 na assinatura do contrato + 1/3 até a decisão de 1º grau + 1/3 ao final"),
						XlsxFicticio.l("Trabalhista", "Reclamação trabalhista — patrocínio do reclamante", "35% a 40% sobre o valor da condenação/acordo", "—", "—", "—",
								"Sem VM fixo; negociar por êxito", "Sem VM fixo", "—", "Sem VM fixo", "Sem entrada; honorário devido ao final, no recebimento do crédito (êxito)"),
						XlsxFicticio.l("Trabalhista", "Razões/contrarrazões de Recurso Ordinário", "—", 30.0, 11433.9, "74", null, 13720.68, 0.05, 13034.646, "1/3 na assinatura"),
						XlsxFicticio.l("Consultoria Preventiva", "Parecer jurídico escrito", "—", 15.0, 5716.95, "113", "Consultoria preventiva contratual/societária", 6860.34, 0.1, 6174.306,
								"Pagamento à vista por serviço prestado ou mensalidade fixa"))
				.aba("Parâmetros",
						XlsxFicticio.l(null, "Parâmetros de Cálculo de Honorários Contratuais"),
						XlsxFicticio.l(null, "Valor da URH vigente (R$)", 381.13, "← campo editável"),
						XlsxFicticio.l(null, "Mês/ano de referência", "Julho/2026"),
						XlsxFicticio.l(null, "Fator de tabela do escritório (markup sobre o piso OAB/DF)", 1.2),
						XlsxFicticio.l(null, "Desconto padrão máximo — demais áreas (pagamento à vista)", 0.1),
						XlsxFicticio.l(null, "Desconto padrão — área Trabalhista", 0.05))
				.bytes();
	}

	static String chave(AreaAtuacao a, String tipo, String fase) {
		return ChaveServico.de(a == null ? null : a.name(), tipo, fase);
	}

	static final String DIVORCIO = "Divórcio consensual, sem bens, mesmo advogado";
	static final String INVENTARIO = "Inventário e arrolamento (judicial ou administrativo)";

	@Test
	@Order(1)
	void importaAsDuasTabelasSeparadasComHistoricoDeVersoes() {
		UsuarioLogado s = socia();
		var r = tabelas.importar(s, "honorarios.xlsx", planilha(), Seccional.DF, true);
		assertThat(r.servicos()).isEqualTo(5);
		assertThat(r.semArea()).isEqualTo(1); // Consultoria Preventiva nao tem area equivalente
		assertThat(r.semValor()).isEqualTo(1); // reclamacao so de exito
		assertThat(jdbc.queryForObject("select count(*) from tabela_honorarios_versao where vigente", Integer.class)).isEqualTo(2);
		var versoes = tabelas.versoes(s);
		assertThat(versoes).extracting(v -> v.tabela() + "/" + v.seccional()).containsExactlyInAnyOrder("OAB/DF", "ESCRITORIO/null");
		var oab = versoes.stream().filter(v -> v.tabela().equals("OAB")).findFirst().orElseThrow();
		assertThat(oab.valorUrh()).isEqualByComparingTo("381.13");
		assertThat(oab.referencia()).isEqualTo("Julho/2026");

		var itensOab = tabelas.itens(s, oab.id(), false);
		var divorcio = itensOab.stream().filter(i -> i.tipoServico().equals(DIVORCIO)).findFirst().orElseThrow();
		assertThat(divorcio.area()).isEqualTo("FAMILIA");
		assertThat(divorcio.valor()).isEqualByComparingTo("15245.20");
		assertThat(divorcio.itemOab()).isEqualTo("40.1-a");
		assertThat(itensOab.stream().filter(i -> i.tipoServico().startsWith("Inventário")).findFirst().orElseThrow().area()).isEqualTo("SUCESSAO");
		assertThat(itensOab.stream().filter(i -> i.tipoServico().startsWith("Parecer")).findFirst().orElseThrow().area()).isNull();
		assertThat(itensOab.stream().filter(i -> i.tipoServico().startsWith("Razões")).findFirst().orElseThrow().fase()).isEqualTo("RECURSO");
		var exito = itensOab.stream().filter(i -> i.tipoServico().startsWith("Reclamação")).findFirst().orElseThrow();
		assertThat(exito.valor()).isNull();
		assertThat(exito.exitoMin()).isEqualByComparingTo("35");
		assertThat(exito.exitoMax()).isEqualByComparingTo("40");

		var esc = versoes.stream().filter(v -> v.tabela().equals("ESCRITORIO")).findFirst().orElseThrow();
		assertThat(esc.fatorEscritorio()).isEqualByComparingTo("1.2");
		var escDivorcio = tabelas.itens(s, esc.id(), false).stream().filter(i -> i.tipoServico().equals(DIVORCIO)).findFirst().orElseThrow();
		assertThat(escDivorcio.valor()).isEqualByComparingTo("18294.24");
		assertThat(escDivorcio.valorSugerido()).isEqualByComparingTo("16464.82");
		assertThat(escDivorcio.derivado()).isTrue();
		assertThat(escDivorcio.chaveServico()).isEqualTo(divorcio.chaveServico()); // pareadas pela chave do servico

		// reimportar: a versao anterior vira historico
		tabelas.importar(s, "honorarios-v2.xlsx", planilha(), Seccional.DF, true);
		assertThat(jdbc.queryForObject("select count(*) from tabela_honorarios_versao", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from tabela_honorarios_versao where vigente", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from tabela_honorarios_versao where not vigente and substituida_em is not null", Integer.class)).isEqualTo(2);
	}

	@Test
	@Order(2)
	void permissoesTabelaCompletaSoSociasAdvogadoVeSoCatalogoSemValores() {
		var u = usuarios.criar(null, "Advogado Atendente", "adv1@exemplo.test", "senha-teste-123456", Perfil.ADVOGADO, null);
		var u2 = usuarios.criar(null, "Outro Advogado", "adv2@exemplo.test", "senha-teste-123456", Perfil.ADVOGADO, null);
		var a = usuarios.criar(null, "Assistente", "assist@exemplo.test", "senha-teste-123456", Perfil.ASSISTENTE, null);
		advogado = new UsuarioLogado(u.id(), u.nome(), u.email(), "x", Perfil.ADVOGADO, true);
		outroAdvogado = new UsuarioLogado(u2.id(), u2.nome(), u2.email(), "x", Perfil.ADVOGADO, true);
		assistente = new UsuarioLogado(a.id(), a.nome(), a.email(), "x", Perfil.ASSISTENTE, true);

		assertThatThrownBy(() -> tabelas.versoes(advogado)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> tabelas.itens(advogado, 1L, false)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> tabelas.importar(advogado, "x.xlsx", planilha(), Seccional.DF, true)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> tabelas.catalogo(assistente)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> relatorios.gerar(advogado, null, null)).isInstanceOf(SecurityException.class);
		var catalogo = tabelas.catalogo(advogado);
		assertThat(catalogo).hasSize(5);
		assertThat(catalogo).extracting(c -> c.tipoServico()).contains(DIVORCIO);
		assertThat(catalogo.get(0).toString()).doesNotContain("15245");
	}

	@Test
	@Order(3)
	void sociasEditamIncluemExcluemItensEUrhRecalcula() {
		UsuarioLogado s = socia();
		var oab = tabelas.versoes(s).stream().filter(v -> v.tabela().equals("OAB") && v.vigente()).findFirst().orElseThrow();
		var esc = tabelas.versoes(s).stream().filter(v -> v.tabela().equals("ESCRITORIO") && v.vigente()).findFirst().orElseThrow();
		var novo = tabelas.criarItem(s, oab.id(), new DadosItem("CRIMINAL", "Habeas corpus", "JUDICIAL", "55", new BigDecimal("20"), new BigDecimal("7622.60"), null, null, null, null, null, null,
				"Valor por ato"));
		assertThat(novo.chaveServico()).isEqualTo(chave(AreaAtuacao.CRIMINAL, "Habeas corpus", "JUDICIAL"));
		var editado = tabelas.atualizarItem(s, novo.id(), new DadosItem("CRIMINAL", "Habeas corpus", "JUDICIAL", "55", new BigDecimal("20"), new BigDecimal("8000.00"), null, null, null, null, null, null,
				"Valor por ato"));
		assertThat(editado.valor()).isEqualByComparingTo("8000.00");
		tabelas.excluirItem(s, novo.id());
		assertThat(tabelas.itens(s, oab.id(), false)).noneMatch(i -> i.id().equals(novo.id()));
		assertThat(tabelas.itens(s, oab.id(), true)).anyMatch(i -> i.id().equals(novo.id()) && !i.ativo());
		assertThatThrownBy(() -> tabelas.criarItem(s, oab.id(), new DadosItem("CRIMINAL", " ", null, null, null, null, null, null, null, null, null, null, null)))
				.isInstanceOf(IllegalArgumentException.class);

		// URH de 381,13 -> 400: o minimo do divorcio (40 URH) vira 16.000 e o valor derivado do escritorio acompanha (x1,2, com 10%)
		tabelas.atualizarUrh(s, oab.id(), new BigDecimal("400"));
		var divorcioOab = tabelas.itens(s, oab.id(), false).stream().filter(i -> i.tipoServico().equals(DIVORCIO)).findFirst().orElseThrow();
		assertThat(divorcioOab.valor()).isEqualByComparingTo("16000.00");
		var divorcioEsc = tabelas.itens(s, esc.id(), false).stream().filter(i -> i.tipoServico().equals(DIVORCIO)).findFirst().orElseThrow();
		assertThat(divorcioEsc.valor()).isEqualByComparingTo("19200.00");
		assertThat(divorcioEsc.valorSugerido()).isEqualByComparingTo("17280.00");
		tabelas.atualizarUrh(s, oab.id(), new BigDecimal("381.13")); // volta ao valor da planilha para os testes seguintes
		assertThat(tabelas.itens(s, esc.id(), false).stream().filter(i -> i.tipoServico().equals(DIVORCIO)).findFirst().orElseThrow().valorSugerido()).isEqualByComparingTo("16464.82");
	}

	@Test
	@Order(4)
	void advogadoVeOQuadroDeSugestaoSoNoAtendimentoQueConduz() {
		UsuarioLogado s = socia();
		clienteId = cadastros.criarCliente(s.id(), "Cliente Ficticio Honorarios", null, null, null, null, null, Set.of(AreaAtuacao.FAMILIA)).id();
		atdId = compromissos.marcar(s, TipoAgendamento.ATD, null, clienteId, "Primeira consulta", advogado.id(), false, LocalDateTime.now().plusDays(1), 45, Modalidade.PRESENCIAL, "Escritorio - sala 1")
				.item().id();

		var p = propostas.sugerirNoAtendimento(advogado, atdId, Seccional.DF, List.of(chave(AreaAtuacao.FAMILIA, DIVORCIO, null)));
		propostaId = p.id();
		assertThat(p.status()).isEqualTo("ABERTA");
		assertThat(p.valorMinimoOab()).isEqualByComparingTo("15245.20");
		assertThat(p.valorSugerido()).isEqualByComparingTo("16464.82");
		assertThat(p.quadro().opcoes()).hasSizeGreaterThanOrEqualTo(5);
		assertThat(p.quadro().linhas().get(0).valorTabela()).isEqualByComparingTo("18294.24");
		assertThat(p.itens()).hasSize(1);
		assertThat(p.tabelaOabVigente()).isTrue();

		// outro advogado e a assistente nao enxergam esse atendimento
		assertThatThrownBy(() -> propostas.sugerirNoAtendimento(outroAdvogado, atdId, Seccional.DF, List.of(chave(AreaAtuacao.FAMILIA, DIVORCIO, null))))
				.isInstanceOf(PropostaNaoEncontradaException.class);
		assertThatThrownBy(() -> propostas.buscar(outroAdvogado, propostaId)).isInstanceOf(PropostaNaoEncontradaException.class);
		assertThatThrownBy(() -> propostas.doAtendimento(assistente, atdId)).isInstanceOf(SecurityException.class);
		assertThat(propostas.doAtendimento(advogado, atdId).id()).isEqualTo(propostaId);

		// seccional sem tabela da OAB cadastrada: avisa, sem piso
		var sp = propostas.recalcular(advogado, propostaId, Seccional.SP, List.of(chave(AreaAtuacao.FAMILIA, DIVORCIO, null)));
		assertThat(sp.tabelaOabVigente()).isFalse();
		assertThat(sp.valorMinimoOab()).isEqualByComparingTo("0");
		assertThat(sp.quadro().linhas().get(0).avisos()).anyMatch(a -> a.contains("Sem valor minimo da OAB"));
		propostas.recalcular(advogado, propostaId, Seccional.DF, List.of(chave(AreaAtuacao.FAMILIA, DIVORCIO, null)));
	}

	@Test
	@Order(5)
	void somaVariosServicosComDescontoDePacoteDefinidoPelasSocias() {
		UsuarioLogado s = socia();
		var esc = tabelas.versoes(s).stream().filter(v -> v.tabela().equals("ESCRITORIO") && v.vigente()).findFirst().orElseThrow();
		tabelas.atualizarPolitica(s, esc.id(), null, null, null, new BigDecimal("0.05"));
		assertThatThrownBy(() -> tabelas.atualizarPolitica(advogado, esc.id(), null, null, null, new BigDecimal("0.5"))).isInstanceOf(SecurityException.class);
		var p = propostas.recalcular(advogado, propostaId, Seccional.DF,
				List.of(chave(AreaAtuacao.FAMILIA, DIVORCIO, null), chave(AreaAtuacao.SUCESSAO, INVENTARIO, null)));
		// minimos: 15.245,20 + 9.528,25 = 24.773,45 ; sugeridos: 16.464,82 + 10.290,51 = 26.755,33 ; -5% = 25.417,56
		assertThat(p.valorMinimoOab()).isEqualByComparingTo("24773.45");
		assertThat(p.itens()).hasSize(2);
		assertThat(p.valorSugerido()).isEqualByComparingTo("25417.56");
		assertThat(p.descontoPacote()).isEqualByComparingTo("0.05");
		assertThat(p.quadro().exito()).anyMatch(e -> e.contains("5% a 10%"));
	}

	@Test
	@Order(6)
	void valorAbaixoDoMinimoDaOabExigeJustificativaEFicaNoRelatorio() {
		var base = new Fechamento(new BigDecimal("20000.00"), new BigDecimal("2000.00"), 6, LocalDate.of(2026, 12, 10), "Entrada + 6 parcelas", null, null);
		assertThatThrownBy(() -> propostas.fechar(advogado, propostaId, base)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("abaixo do minimo da OAB")
				.hasMessageContaining("4773.45").hasMessageContaining("justificativa");
		assertThat(jdbc.queryForObject("select count(*) from proposta_honorarios where status = 'FECHADA'", Integer.class)).isZero();
		assertThatThrownBy(() -> propostas.fechar(advogado, propostaId,
				new Fechamento(base.valorFechado(), base.entrada(), base.parcelas(), base.primeiroVencimento(), base.formaPagamento(), "PACOTE", " ")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> propostas.fechar(outroAdvogado, propostaId, base)).isInstanceOf(PropostaNaoEncontradaException.class);

		var f = propostas.fechar(advogado, propostaId, new Fechamento(base.valorFechado(), base.entrada(), base.parcelas(), base.primeiroVencimento(), base.formaPagamento(), "HIPOSSUFICIENTE",
				"Cliente hipossuficiente, sem renda formal"));
		assertThat(f.status()).isEqualTo("FECHADA");
		assertThat(f.abaixoDaOab()).isTrue();
		assertThat(f.diferencaAbaixoOab()).isEqualByComparingTo("4773.45");
		assertThat(f.valorSugerido()).as("o sugerido continua guardado").isEqualByComparingTo("25417.56");
		assertThat(f.valorFechado()).isEqualByComparingTo("20000.00");
		assertThat(jdbc.queryForObject("select count(*) from log_auditoria where acao = 'PROPOSTA_FECHADA_ABAIXO_OAB'", Integer.class)).isEqualTo(1);

		var rel = relatorios.gerar(socia(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
		assertThat(rel.abaixoDaTabela()).hasSize(1);
		assertThat(rel.abaixoDaTabela().get(0).justificativa()).contains("hipossuficiente");
		assertThat(rel.abaixoDaTabela().get(0).tipo()).isEqualTo("HIPOSSUFICIENTE");
		assertThat(rel.abaixoDaTabela().get(0).diferenca()).isEqualByComparingTo("4773.45");
	}

	@Test
	@Order(7)
	void minutaDoContratoPreenchidaFicaNaPastaDoClienteRestritaAsSocias() throws IOException {
		var m = minuta.gerar(advogado, propostaId);
		assertThat(m.nomeArquivo()).endsWith(".docx");
		String xml = lerDocumento(m.conteudo());
		assertThat(xml).contains("Cliente Ficticio Honorarios").contains("R$ 20.000,00").contains("vinte mil reais").contains("Divórcio consensual").contains("Entrada").contains("6 parcela(s)");
		assertThat(xml).doesNotContain("{{");
		assertThat(jdbc.queryForObject("select count(*) from documento_cliente where cliente_id = ? and tipo = 'CONTRATO_HONORARIOS' and restrito_socias", Integer.class, clienteId)).isEqualTo(1);
		assertThatThrownBy(() -> minuta.gerar(outroAdvogado, propostaId)).isInstanceOf(PropostaNaoEncontradaException.class);
		assertThatThrownBy(() -> minuta.modelo(advogado)).isInstanceOf(SecurityException.class);
		minuta.salvarModelo(socia(), "CONTRATO PERSONALIZADO para {{cliente_nome}} no valor de {{valor_total}}. {{campo_inexistente}}");
		String personalizado = lerDocumento(minuta.gerar(socia(), propostaId).conteudo());
		assertThat(personalizado).contains("CONTRATO PERSONALIZADO para Cliente Ficticio Honorarios").contains("[campo_inexistente]");
	}

	static String lerDocumento(byte[] docx) throws IOException {
		try (var z = new ZipInputStream(new ByteArrayInputStream(docx))) {
			var e = z.getNextEntry();
			while (e != null && !e.getName().equals("word/document.xml")) {
				e = z.getNextEntry();
			}
			assertThat(e).isNotNull();
			return new String(z.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
	}

	@Test
	@Order(8)
	void assinarLancaContratoEParcelasPorAreaNoFinanceiro() {
		assertThatThrownBy(() -> propostas.assinar(advogado, propostaId, LocalDate.of(2026, 12, 1))).isInstanceOf(SecurityException.class);
		var p = propostas.assinar(socia(), propostaId, LocalDate.of(2026, 12, 1));
		assertThat(p.contratoLancado()).isTrue();
		assertThat(p.dataAssinatura()).isEqualTo(LocalDate.of(2026, 12, 1));

		// dois servicos em duas areas: um contrato por area, repartidos pelo valor sugerido
		var contratos = jdbc.queryForList("select id, area, valor_total from contrato_honorarios where proposta_id = ? order by area", propostaId);
		assertThat(contratos).hasSize(2);
		assertThat(contratos).extracting(c -> c.get("area")).containsExactly("FAMILIA", "SUCESSAO");
		BigDecimal soma = contratos.stream().map(c -> (BigDecimal) c.get("valor_total")).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(soma).isEqualByComparingTo("20000.00");
		for (var c : contratos) {
			Long id = ((Number) c.get("id")).longValue();
			var parcelas = jdbc.queryForList("select numero, rotulo, valor, situacao from parcela where contrato_id = ? order by numero", id);
			assertThat(parcelas).hasSize(7); // entrada + 6
			assertThat(parcelas.get(0).get("rotulo")).isEqualTo("ENTRADA");
			assertThat(parcelas).allSatisfy(x -> assertThat(x.get("situacao")).isEqualTo("ABERTA"));
			BigDecimal total = parcelas.stream().map(x -> (BigDecimal) x.get("valor")).reduce(BigDecimal.ZERO, BigDecimal::add);
			assertThat(total).isEqualByComparingTo((BigDecimal) c.get("valor_total"));
		}
		assertThat(jdbc.queryForObject("select count(*) from cliente_area where cliente_id = ? and area in ('FAMILIA', 'SUCESSAO')", Integer.class, clienteId)).isEqualTo(2);
		assertThatThrownBy(() -> propostas.assinar(socia(), propostaId, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ja foi assinado");
		assertThatThrownBy(() -> propostas.fechar(advogado, propostaId, new Fechamento(new BigDecimal("30000"), null, 0, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@Order(9)
	void relatoriosSugeridoXFechadoConversaoETicketMedio() {
		UsuarioLogado s = socia();
		// uma reuniao realizada (atendimento concluido) do advogado, ja com proposta fechada; e outra reuniao sem proposta fechada
		jdbc.update("insert into atendimento (cliente_id, momento, canal, atendente_id, resumo, origem, agendamento_id, criado_por) values (?, now(), 'PRESENCIAL', ?, 'Reuniao realizada', 'AGENDAMENTO', ?, ?)",
				clienteId, advogado.id(), atdId, advogado.id());
		Long atd2 = compromissos.marcar(s, TipoAgendamento.ATD, null, clienteId, "Segunda consulta", advogado.id(), false, LocalDateTime.now().plusDays(2), 45, Modalidade.ONLINE, "https://exemplo.test/sala")
				.item().id();
		jdbc.update("insert into atendimento (cliente_id, momento, canal, atendente_id, resumo, origem, agendamento_id, criado_por) values (?, now(), 'ONLINE', ?, 'Outra reuniao', 'AGENDAMENTO', ?, ?)",
				clienteId, advogado.id(), atd2, advogado.id());
		var p2 = propostas.sugerirNoAtendimento(advogado, atd2, Seccional.DF, List.of(chave(AreaAtuacao.FAMILIA, DIVORCIO, null)));
		propostas.naoFechada(advogado, p2.id());

		var rel = s == null ? null : relatorios.gerar(s, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
		assertThat(rel.conversaoGeral().reunioes()).isEqualTo(2);
		assertThat(rel.conversaoGeral().fechadas()).isEqualTo(1);
		assertThat(rel.conversaoGeral().taxa()).isEqualByComparingTo("50.0");
		assertThat(rel.conversao()).extracting(c -> c.advogado()).containsExactly("Advogado Atendente");

		assertThat(rel.sugeridoXFechado()).hasSize(2); // familia e sucessao, no mes corrente
		BigDecimal sugerido = rel.sugeridoXFechado().stream().map(x -> x.sugerido()).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal fechado = rel.sugeridoXFechado().stream().map(x -> x.fechado()).reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(sugerido).isEqualByComparingTo("25417.56");
		assertThat(fechado).isEqualByComparingTo("20000.00");
		assertThat(rel.sugeridoXFechado()).allSatisfy(x -> assertThat(x.advogado()).isEqualTo("Advogado Atendente"));
		assertThat(rel.ticketMedio()).extracting(t -> t.area()).containsExactlyInAnyOrder("FAMILIA", "SUCESSAO");
		assertThat(rel.ticketMedio().stream().map(t -> t.total()).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("20000.00");
		assertThat(rel.ticketMedio()).allSatisfy(t -> assertThat(t.ticketMedio()).isEqualByComparingTo(t.total())); // 1 contrato por area
		assertThat(propostas.listar(s)).hasSize(2);
		assertThatThrownBy(() -> propostas.listar(advogado)).isInstanceOf(SecurityException.class);
	}
}
