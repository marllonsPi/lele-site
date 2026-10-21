package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.documentos.DocumentoClienteService;
import br.com.crispimcampos.gestao.documentos.TipoDocumento;
import br.com.crispimcampos.gestao.honorarios.PropostaDtos.PropostaDto;
import br.com.crispimcampos.gestao.sistema.ConfiguracaoService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minuta do contrato de honorarios a partir do modelo do escritorio (texto com {{campos}}, editavel pelas socias), preenchida com
 * cliente, servicos, valor, forma de pagamento e parcelas, entregue em .docx para o advogado revisar. Cada minuta gerada fica na
 * pasta do cliente como contrato (restrito as socias, porque traz valores). Enquanto o escritorio nao cadastrar seu modelo
 * oficial, vale o modelo-padrao abaixo, que e apenas um ponto de partida.
 */
@Service
public class MinutaContratoService {

	static final String MODELO_PADRAO = """
			# CONTRATO DE HONORÁRIOS ADVOCATÍCIOS

			(MINUTA GERADA AUTOMATICAMENTE — conferir e ajustar ao modelo oficial do escritório antes de assinar)

			CONTRATANTE: {{cliente_nome}}, {{cliente_estado_civil}}, portador(a) do RG nº {{cliente_rg}} e inscrito(a) no CPF/CNPJ nº {{cliente_documento}}, residente e domiciliado(a) em {{cliente_endereco}}, telefone {{cliente_telefone}}, e-mail {{cliente_email}}.

			CONTRATADO: {{escritorio_nome}}, por seu(sua) advogado(a) {{advogado_nome}}.

			# CLÁUSULA 1ª — DO OBJETO
			O CONTRATADO prestará ao CONTRATANTE serviços advocatícios relativos a: {{servicos}}.

			# CLÁUSULA 2ª — DOS HONORÁRIOS
			Pelos serviços descritos, o CONTRATANTE pagará ao CONTRATADO honorários no valor total de {{valor_total}} ({{valor_extenso}}).

			# CLÁUSULA 3ª — DA FORMA DE PAGAMENTO
			{{forma_pagamento}}
			{{parcelas}}

			# CLÁUSULA 4ª — DOS HONORÁRIOS DE ÊXITO
			{{exito}}

			# CLÁUSULA 5ª — DAS DISPOSIÇÕES GERAIS
			Salvo estipulação em contrário, os honorários não incluem custas, despesas processuais e diligências, que serão reembolsadas pelo CONTRATANTE. A tabela de honorários da OAB da seccional respectiva é aplicada subsidiariamente.

			{{local_data}}

			______________________________          ______________________________
			CONTRATANTE                                              CONTRATADO
			""";

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final JdbcTemplate jdbc;
	private final PropostaService propostas;
	private final DocumentoClienteService documentos;
	private final ConfiguracaoService configuracao;
	private final AuditoriaService auditoria;

	MinutaContratoService(JdbcTemplate jdbc, PropostaService propostas, DocumentoClienteService documentos, ConfiguracaoService configuracao, AuditoriaService auditoria) {
		this.jdbc = jdbc;
		this.propostas = propostas;
		this.documentos = documentos;
		this.configuracao = configuracao;
		this.auditoria = auditoria;
	}

	public record Minuta(String nomeArquivo, byte[] conteudo) {
	}

	@Transactional(readOnly = true)
	public String modelo(UsuarioLogado u) {
		exigirSocia(u);
		return modeloVigente();
	}

	@Transactional
	public void salvarModelo(UsuarioLogado u, String texto) {
		exigirSocia(u);
		if (texto == null || texto.isBlank() || texto.length() > 60_000) {
			throw new IllegalArgumentException("Texto do modelo invalido (vazio ou maior que 60 mil caracteres)");
		}
		jdbc.update("insert into modelo_contrato_honorarios (texto, atualizado_por) values (?, ?)", texto, u.id());
		auditoria.registrar(u.id(), "MODELO_CONTRATO_ATUALIZADO", "modelo_contrato_honorarios", null, null);
	}

	/** Gera a minuta da proposta (precisa estar fechada) e a guarda na pasta do cliente. */
	@Transactional
	public Minuta gerar(UsuarioLogado u, Long propostaId) {
		PropostaDto p = propostas.buscar(u, propostaId); // checa o acesso: socia ou advogado do atendimento
		if (p.valorFechado() == null) {
			throw new IllegalArgumentException("Feche a proposta (valor negociado) antes de gerar a minuta");
		}
		var c = jdbc.queryForMap("select nome, documento, rg, estado_civil, endereco, telefone, email from cliente where id = ?", p.clienteId());
		var adv = jdbc.queryForList("select nome from usuario where id = ?", String.class, p.advogadoId());
		Map<String, String> campos = new LinkedHashMap<>();
		campos.put("cliente_nome", texto(c.get("nome"), "[nome do cliente]"));
		campos.put("cliente_documento", documento(texto(c.get("documento"), null)));
		campos.put("cliente_rg", texto(c.get("rg"), "[RG]"));
		campos.put("cliente_estado_civil", texto(c.get("estado_civil"), "[estado civil]"));
		campos.put("cliente_endereco", texto(c.get("endereco"), "[endereço]"));
		campos.put("cliente_telefone", texto(c.get("telefone"), "[telefone]"));
		campos.put("cliente_email", texto(c.get("email"), "[e-mail]"));
		campos.put("escritorio_nome", configuracao.valor(ConfiguracaoService.ESCRITORIO_NOME, "Crispim & Campos Advocacia"));
		campos.put("advogado_nome", adv.isEmpty() ? "[advogado(a)]" : adv.get(0));
		campos.put("servicos", String.join("; ", p.itens().stream().map(i -> i.tipoServico()).toList()));
		campos.put("valor_total", moeda(p.valorFechado()));
		campos.put("valor_extenso", ValorPorExtenso.de(p.valorFechado()));
		campos.put("forma_pagamento", p.formaPagamento() != null && !p.formaPagamento().isBlank() ? p.formaPagamento() : "O pagamento será feito conforme o cronograma abaixo.");
		campos.put("parcelas", cronograma(p));
		campos.put("exito", exito(p));
		campos.put("local_data", "Brasília/DF, " + LocalDate.now().format(DATA) + ".");
		String texto = preencher(modeloVigente(), campos);
		byte[] docx = docx(texto);
		String nome = "Minuta contrato de honorarios - " + p.clienteNome().replaceAll("[^\\p{L}\\p{N} ]", "") + ".docx";
		documentos.anexar(u.id(), p.clienteId(), TipoDocumento.CONTRATO_HONORARIOS, nome, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx, "MANUAL", null, null);
		auditoria.registrar(u.id(), "MINUTA_CONTRATO_GERADA", "proposta_honorarios", String.valueOf(propostaId), null);
		return new Minuta(nome, docx);
	}

	// ---- montagem

	static String preencher(String modelo, Map<String, String> campos) {
		Matcher m = Pattern.compile("\\{\\{\\s*([a-z_]+)\\s*}}").matcher(modelo);
		StringBuilder s = new StringBuilder();
		while (m.find()) {
			String v = campos.get(m.group(1));
			m.appendReplacement(s, Matcher.quoteReplacement(v == null ? "[" + m.group(1) + "]" : v));
		}
		m.appendTail(s);
		return s.toString();
	}

	private static String cronograma(PropostaDto p) {
		StringBuilder b = new StringBuilder();
		BigDecimal entrada = p.entrada() == null ? BigDecimal.ZERO : p.entrada();
		int n = p.parcelas() == null ? 0 : p.parcelas();
		if (entrada.signum() > 0) {
			b.append("Entrada: ").append(moeda(entrada)).append(" (").append(ValorPorExtenso.de(entrada)).append("), na assinatura deste contrato.\n");
		}
		BigDecimal resto = p.valorFechado().subtract(entrada);
		if (n > 0 && resto.signum() > 0) {
			BigDecimal parcela = resto.divide(BigDecimal.valueOf(n), 2, java.math.RoundingMode.DOWN);
			b.append(n).append(" parcela(s) mensais de ").append(moeda(parcela)).append(", a primeira com vencimento em ")
					.append(p.primeiroVencimento() == null ? "[data]" : p.primeiroVencimento().format(DATA)).append(" (a última ajustada ao total).");
		} else if (resto.signum() > 0) {
			b.append("Pagamento à vista: ").append(moeda(resto)).append(".");
		}
		return b.toString().trim();
	}

	private static String exito(PropostaDto p) {
		List<String> e = p.itens().stream().filter(i -> i.exitoTexto() != null).map(i -> i.tipoServico() + ": " + i.exitoTexto()).toList();
		return e.isEmpty() ? "Não há cláusula de êxito neste contrato." : "Além dos honorários fixos, poderão ser pactuados honorários de êxito: " + String.join("; ", e) + ".";
	}

	private String modeloVigente() {
		return jdbc.queryForList("select texto from modelo_contrato_honorarios order by id desc limit 1", String.class).stream().findFirst().orElse(MODELO_PADRAO);
	}

	private static String moeda(BigDecimal v) {
		return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(v).replace(' ', ' ');
	}

	private static String documento(String d) {
		if (d == null) {
			return "[CPF/CNPJ]";
		}
		return d.length() == 11 ? d.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4")
				: d.length() == 14 ? d.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5") : d;
	}

	private static String texto(Object o, String padrao) {
		return o == null || String.valueOf(o).isBlank() ? padrao : String.valueOf(o);
	}

	private static void exigirSocia(UsuarioLogado u) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("O modelo do contrato e restrito as socias");
		}
	}

	/** .docx minimo (sem biblioteca): linhas com "# " viram titulo em negrito; as demais, paragrafos. */
	static byte[] docx(String texto) {
		StringBuilder corpo = new StringBuilder();
		for (String linha : texto.split("\n", -1)) {
			boolean titulo = linha.startsWith("# ");
			String t = titulo ? linha.substring(2) : linha;
			corpo.append("<w:p>").append(titulo ? "<w:pPr><w:jc w:val=\"center\"/></w:pPr>" : "").append("<w:r>").append(titulo ? "<w:rPr><w:b/></w:rPr>" : "")
					.append("<w:t xml:space=\"preserve\">").append(esc(t)).append("</w:t></w:r></w:p>");
		}
		String documento = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
				+ corpo + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1417\" w:right=\"1134\" w:bottom=\"1417\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>";
		String tipos = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
				+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>";
		String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" "
				+ "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>";
		try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream z = new ZipOutputStream(out)) {
			put(z, "[Content_Types].xml", tipos);
			put(z, "_rels/.rels", rels);
			put(z, "word/document.xml", documento);
			z.finish();
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void put(ZipOutputStream z, String nome, String conteudo) throws IOException {
		z.putNextEntry(new ZipEntry(nome));
		z.write(conteudo.getBytes(StandardCharsets.UTF_8));
		z.closeEntry();
	}

	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
