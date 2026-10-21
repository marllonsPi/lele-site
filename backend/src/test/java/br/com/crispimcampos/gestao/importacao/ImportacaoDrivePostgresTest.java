package br.com.crispimcampos.gestao.importacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.documentos.DocumentoClienteService;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Acao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Tipo;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Importacao das pastas de clientes do Drive em Postgres real, com Drive simulado e arquivos ficticios. */
@SpringBootTest(properties = {"app.bootstrap.mestre.nome=Socia Teste", "app.bootstrap.mestre.email=socia@exemplo.test",
		"app.bootstrap.mestre.senha=senha-socia-teste-1"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportacaoDrivePostgresTest {

	static final EmbeddedPostgres PG = iniciar();

	static EmbeddedPostgres iniciar() {
		try {
			return EmbeddedPostgres.builder().setDataDirectory(Files.createTempDirectory("pg-drive")).start();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void banco(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
		r.add("spring.datasource.username", () -> "postgres");
		r.add("spring.datasource.password", () -> "postgres");
		r.add("STORAGE_DIR", () -> "target/teste-storage-drive");
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
	@Autowired
	DocumentoClienteService documentos;
	@MockitoBean
	DriveFontes fontes;

	static final String CNJ_ANA = XlsxFicticio.CNJ_MODELO;
	static final String CNJ_NOVO = "0000777-" + br.com.crispimcampos.gestao.cadastros.NumeroCnj.calcularDv("0000777", "20268070001") + ".2026.8.07.0001";
	static final String CNJ_SOLTO = "0000888-" + br.com.crispimcampos.gestao.cadastros.NumeroCnj.calcularDv("0000888", "20268070001") + ".2026.8.07.0001";

	static final Map<String, byte[]> CONTEUDO = new HashMap<>();

	static DriveFonte.Arquivo arq(String id, String nome, String mime, List<String> caminho, String texto) {
		byte[] b = texto == null ? new byte[] {1, 2, 3} : texto.getBytes(StandardCharsets.UTF_8);
		CONTEUDO.put(id, b);
		return new DriveFonte.Arquivo(id, nome, mime, Instant.parse("2026-09-01T10:00:00Z"), b.length, caminho);
	}

	static String contratoAna() {
		return """
				CONTRATO DE HONORARIOS
				CONTRATANTE: ANA EXEMPLO DA SILVA, brasileira, solteira, portadora do RG nº 9.876.543 SSP/DF, inscrita no CPF sob o nº 529.982.247-25,
				residente e domiciliada na Quadra 1 Casa 2, Taguatinga/DF, e-mail ana@exemplo.test.
				CONTRATADAS: Sociedade de Advocacia Exemplo, CNPJ 11.222.333/0001-81.
				Os honorarios sao de R$ 7.000,00 em 24 parcelas mensais de R$ 250,00. Processo %s.
				Brasilia, 10 de abril de 2026.
				""".formatted(CNJ_ANA);
	}

	void fonteSimulada() {
		fonteSimulada(null);
	}

	void fonteSimulada(DriveFonte.Arquivo extra) {
		List<DriveFonte.Arquivo> arquivos = List.of(
				arq("f1", "Contrato de honorarios.txt", "text/plain", List.of("ANA EXEMPLO DA SILVA - 529.982.247-25"), contratoAna()),
				arq("f2", "RG.jpg", "image/jpeg", List.of("ANA EXEMPLO DA SILVA - 529.982.247-25"), null),
				arq("f3", "Procuracao.txt", "text/plain", List.of("ANA EXEMPLO DA SILVA - 529.982.247-25", "Docs"),
						"PROCURACAO OUTORGANTE: ANA EXEMPLO DA SILVA, CPF 529.982.247-25, telefone (61) 98888-7777. Processo " + CNJ_NOVO + "."),
				arq("f4", "Contrato.txt", "text/plain", List.of("PEDRO NOVO CLIENTE"),
						"CONTRATANTE: PEDRO NOVO CLIENTE, casado, CPF 390.533.447-05, residente e domiciliado na Rua 5 Lote 3, Gama/DF. Honorarios de R$ 3.000,00 em 3 parcelas mensais de R$ 1.000,00. Brasilia, 2 de maio de 2026."),
				arq("f5", "processo " + CNJ_SOLTO + ".txt", "text/plain", List.of(), null),
				arq("f6", "Contrato gigante.pdf", "application/pdf", List.of("PEDRO NOVO CLIENTE"), null));
		List<DriveFonte.Arquivo> todos = new java.util.ArrayList<>(arquivos);
		if (extra != null) {
			todos.add(extra);
		}
		todos.set(5, new DriveFonte.Arquivo("f6", "Contrato gigante.pdf", "application/pdf", null, 30L * 1024 * 1024, List.of("PEDRO NOVO CLIENTE")));
		var fonte = new DriveFonte() {
			final List<Arquivo> lista = List.copyOf(todos);

			@Override
			public List<Arquivo> listar() {
				return lista;
			}

			@Override
			public byte[] baixar(Arquivo a) {
				return CONTEUDO.get(a.id());
			}
		};
		when(fontes.para(any())).thenReturn(fonte);
	}

	UsuarioLogado socia() {
		return (UsuarioLogado) detalhes.loadUserByUsername("socia@exemplo.test");
	}

	static ItemPrevia achar(ImportacaoDto d, Tipo tipo, String trecho) {
		return d.itens().stream().filter(i -> i.tipo() == tipo && i.rotulo().contains(trecho)).findFirst().orElseThrow();
	}

	@Test
	@Order(1)
	void previaDoDriveMostraConflitosDivergenciasEPendencias() {
		UsuarioLogado s = socia();
		// carga inicial pela planilha: cria Ana (sem CPF), contrato de 6.000 e o processo
		ImportacaoDto planilha = servico.gerarPreviaPlanilha(s, "p.xlsx", XlsxFicticio.planilhaModelo(), null);
		for (ItemPrevia i : planilha.itens()) {
			if (i.tipo() == Tipo.CLIENTE && i.rotulo().contains("CARLA")) {
				servico.decidir(s, planilha.id(), i.id(), null, Map.of("areas", List.of("CIVEL")));
			} else if (i.tipo() == Tipo.CLIENTE && i.rotulo().contains("DANIEL")) {
				servico.decidir(s, planilha.id(), i.id(), null, Map.of("areas", List.of("CRIMINAL")));
			} else if (i.tipo() == Tipo.CONTRATO && i.rotulo().contains("CARLA")) {
				servico.decidir(s, planilha.id(), i.id(), null, Map.of("area", "CIVEL"));
			} else if (i.tipo() == Tipo.PARCELA && i.rotulo().contains("BRUNO")) {
				servico.decidir(s, planilha.id(), i.id(), null, Map.of("vencimento", "2026-04-15"));
			}
		}
		servico.confirmar(s, planilha.id());

		fonteSimulada();
		ImportacaoDto d = servico.gerarPreviaDrive(s, null);
		assertThat(d.origem()).isEqualTo("DRIVE");
		assertThat(jdbc.queryForObject("select count(*) from documento_cliente", Integer.class)).isZero();

		ItemPrevia ana = achar(d, Tipo.CLIENTE, "ANA EXEMPLO");
		assertThat(ana.acao()).isEqualTo(Acao.CONFLITO); // sistema sem CPF/RG: o Drive preenche
		assertThat(ana.importado()).containsEntry("documento", "52998224725").containsEntry("estadoCivil", "solteira");
		assertThat(ana.divergencias()).anyMatch(x -> x.contains("CPF/CNPJ: vazio no sistema"));

		ItemPrevia contrato = achar(d, Tipo.CONTRATO, "ANA EXEMPLO");
		assertThat(contrato.acao()).isEqualTo(Acao.CONFLITO);
		assertThat(contrato.divergencias()).anyMatch(x -> x.contains("Valor do contrato no Drive (R$ 7000.00) diferente"));
		assertThat(contrato.existente()).containsKey("id");

		ItemPrevia novo = achar(d, Tipo.CLIENTE, "PEDRO");
		assertThat(novo.acao()).isEqualTo(Acao.CRIAR);
		assertThat(achar(d, Tipo.CONTRATO, "PEDRO").acao()).isEqualTo(Acao.CRIAR);
		assertThat(d.itens().stream().filter(i -> i.tipo() == Tipo.PROCESSO && i.acao() == Acao.CRIAR)).hasSize(1); // CNJ_NOVO na procuracao da Ana
		assertThat(d.itens()).anyMatch(i -> i.tipo() == Tipo.PROCESSO && i.acao() == Acao.IGNORAR && i.divergencias().get(0).contains("sem cliente vinculado"));
		assertThat(d.itens()).anyMatch(i -> i.tipo() == Tipo.DOCUMENTO && i.acao() == Acao.IGNORAR && i.divergencias().get(0).contains("maior que 20 MB"));
		assertThat(achar(d, Tipo.DOCUMENTO, "Contrato de honorarios").importado()).containsEntry("restritoSocias", true);
		assertThat(d.pendencias()).anyMatch(x -> x.contains("PEDRO")); // area do cliente novo
		assertThatThrownBy(() -> servico.confirmar(s, d.id())).isInstanceOf(PendenciaDeImportacaoException.class);
	}

	@Test
	@Order(2)
	void confirmaPreenchendoVaziosSemSobrescreverEAnexandoDocumentos() {
		UsuarioLogado s = socia();
		fonteSimulada();
		Long id = servico.listar(s).get(0).id();
		ImportacaoDto d = servico.detalhe(s, id);
		servico.decidir(s, id, achar(d, Tipo.CLIENTE, "PEDRO").id(), null, Map.of("areas", List.of("CIVEL")));
		servico.decidir(s, id, achar(d, Tipo.CLIENTE, "ANA").id(), br.com.crispimcampos.gestao.importacao.ItemPrevia.Decisao.APLICAR, Map.of());
		servico.decidir(s, id, achar(d, Tipo.CONTRATO, "ANA").id(), br.com.crispimcampos.gestao.importacao.ItemPrevia.Decisao.APLICAR, Map.of());
		d = servico.detalhe(s, id);
		assertThat(d.pendencias()).isEmpty();

		ImportacaoDto fim = servico.confirmar(s, id);
		assertThat((List<?>) fim.relatorio().get("erros")).isEmpty();

		// Ana: CPF/RG/telefone preenchidos (estavam vazios); o nome nao muda
		var ana = jdbc.queryForMap("select documento, rg, estado_civil, telefone, email from cliente where nome = 'ANA EXEMPLO DA SILVA'");
		assertThat(ana.get("documento")).isEqualTo("52998224725");
		assertThat((String) ana.get("rg")).startsWith("9.876.543");
		assertThat(ana.get("estado_civil")).isEqualTo("solteira");
		assertThat((String) ana.get("telefone")).contains("98888-7777");

		// contrato da planilha: valor mantido em 6.000 (nao sobrescreveu), data de assinatura e forma preenchidas
		var ct = jdbc.queryForMap("select valor_total, data_assinatura, numero_parcelas from contrato_honorarios where chave_origem like 'PLANILHA|ANA%'");
		assertThat((java.math.BigDecimal) ct.get("valor_total")).isEqualByComparingTo("6000");
		assertThat(String.valueOf(ct.get("data_assinatura"))).isEqualTo("2026-04-10");
		assertThat(jdbc.queryForObject("select count(*) from contrato_honorarios where chave_origem like 'DRIVE|%'", Integer.class)).isEqualTo(1); // Pedro

		// documentos anexados: 3 da Ana + 1 do Pedro; o contrato de honorarios e restrito as socias
		assertThat(jdbc.queryForObject("select count(*) from documento_cliente", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from documento_cliente where restrito_socias and tipo = 'CONTRATO_HONORARIOS'", Integer.class)).isEqualTo(2);
		Long clienteAna = jdbc.queryForObject("select id from cliente where nome = 'ANA EXEMPLO DA SILVA'", Long.class);
		assertThat(documentos.listar(s, clienteAna)).hasSize(3);
		assertThat(documentos.listar(s, clienteAna)).anySatisfy(x -> assertThat(x.restritoSocias()).isTrue());

		// processo da procuracao criado, vinculado a Ana, com a area dela
		assertThat(jdbc.queryForObject("select count(*) from processo where numero = ? and area = 'FAMILIA'", Integer.class, CNJ_NOVO.replaceAll("\\D", ""))).isEqualTo(1);
		var advogado = new UsuarioLogado(99L, "Adv", "adv@exemplo.test", "x", Perfil.ADVOGADO, true);
		assertThatThrownBy(() -> documentos.listar(advogado, clienteAna)).as("sem visibilidade do cliente: inexistente").isInstanceOf(RuntimeException.class);
	}

	@Test
	@Order(3)
	void reimportarODriveNaoDuplicaDocumentosNemProcessos() {
		UsuarioLogado s = socia();
		fonteSimulada();
		ImportacaoDto d = servico.gerarPreviaDrive(s, null);
		assertThat(d.itens().stream().filter(i -> i.tipo() == Tipo.DOCUMENTO && i.acao() == Acao.CRIAR)).isEmpty();
		assertThat(d.itens().stream().filter(i -> i.tipo() == Tipo.PROCESSO && i.acao() == Acao.CRIAR)).isEmpty();
		assertThat(d.itens().stream().filter(i -> i.tipo() == Tipo.CLIENTE && i.acao() == Acao.CRIAR)).isEmpty();
		servico.confirmar(s, d.id());
		assertThat(jdbc.queryForObject("select count(*) from documento_cliente", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from cliente", Integer.class)).isEqualTo(5);
	}

	@Test
	@Order(4)
	void sincronizacaoDiariaAplicaSoONovoSemRiscoERegistraNoHistorico() {
		UsuarioLogado s = socia();
		when(fontes.configurada()).thenReturn(true);
		fonteSimulada();
		var r = servico.sincronizar(s);
		assertThat(r).extracting(ImportacaoService.ResultadoSincronizacao::situacao).containsExactly("SEM_MUDANCA");

		// um documento novo na pasta da Ana: novidade sem risco, aplicada sozinha
		fonteSimulada(arq("f7", "CNH.jpg", "image/jpeg", List.of("ANA EXEMPLO DA SILVA - 529.982.247-25"), null));
		var depois = servico.sincronizar(s);
		assertThat(depois).extracting(ImportacaoService.ResultadoSincronizacao::situacao).containsExactly("APLICADA");
		assertThat(jdbc.queryForObject("select count(*) from documento_cliente", Integer.class)).isEqualTo(5);
		assertThat(jdbc.queryForObject("select modo from importacao where id = ?", String.class, depois.get(0).importacaoId())).isEqualTo("DIARIA");
		assertThat(jdbc.queryForObject("select status from importacao where id = ?", String.class, depois.get(0).importacaoId())).isEqualTo("CONCLUIDA");

		// cliente novo no Drive: exige confirmacao (area, conferencia): fica como previa
		fonteSimulada(arq("f8", "Contrato.txt", "text/plain", List.of("MARIA OUTRA CLIENTE"), "CONTRATANTE: MARIA OUTRA CLIENTE, solteira. Honorarios de R$ 1.000,00."));
		var pendente = servico.sincronizar(s);
		assertThat(pendente).extracting(ImportacaoService.ResultadoSincronizacao::situacao).containsExactly("AGUARDA_CONFIRMACAO");
		assertThat(jdbc.queryForObject("select status from importacao where id = ?", String.class, pendente.get(0).importacaoId())).isEqualTo("PREVIA");
		assertThat(jdbc.queryForObject("select count(*) from cliente where nome like 'MARIA OUTRA%'", Integer.class)).isZero();
	}
}
