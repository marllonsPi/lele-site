package br.com.crispimcampos.gestao.importacao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Gera .xlsx minimo e ficticio (texto inline e numeros) para testar leitor e importacao sem dado real. */
public final class XlsxFicticio {

	/** CNJ ficticio com digito verificador valido. */
	static final String CNJ_MODELO = "0000001-" + br.com.crispimcampos.gestao.cadastros.NumeroCnj.calcularDv("0000001", "20258070001") + ".2025.8.07.0001";

	private final Map<String, List<List<Object>>> abas = new LinkedHashMap<>();

	public XlsxFicticio aba(String nome, List<Object>... linhas) {
		abas.put(nome, new ArrayList<>(List.of(linhas)));
		return this;
	}

	public static List<Object> l(Object... celulas) {
		return java.util.Arrays.asList(celulas);
	}

	public byte[] bytes() {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream z = new ZipOutputStream(out)) {
			StringBuilder wb = new StringBuilder("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
			StringBuilder rels = new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
			int i = 1;
			for (String nome : abas.keySet()) {
				wb.append("<sheet name=\"").append(esc(nome)).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
				rels.append("<Relationship Id=\"rId").append(i).append("\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
				i++;
			}
			wb.append("</sheets></workbook>");
			rels.append("</Relationships>");
			put(z, "xl/workbook.xml", wb.toString());
			put(z, "xl/_rels/workbook.xml.rels", rels.toString());
			i = 1;
			for (List<List<Object>> linhas : abas.values()) {
				StringBuilder s = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
				int r = 1;
				for (List<Object> linha : linhas) {
					s.append("<row r=\"").append(r).append("\">");
					for (int c = 0; c < linha.size(); c++) {
						Object v = linha.get(c);
						if (v == null) {
							continue;
						}
						String ref = (char) ('A' + c) + "" + r;
						if (v instanceof Number) {
							s.append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>");
						} else {
							s.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>").append(esc(String.valueOf(v))).append("</t></is></c>");
						}
					}
					s.append("</row>");
					r++;
				}
				s.append("</sheetData></worksheet>");
				put(z, "xl/worksheets/sheet" + i + ".xml", s.toString());
				i++;
			}
			z.finish();
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void put(ZipOutputStream z, String nome, String conteudo) throws IOException {
		z.putNextEntry(new ZipEntry(nome));
		z.write(conteudo.getBytes(StandardCharsets.UTF_8));
		z.closeEntry();
	}

	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/** Planilha ficticia no mesmo formato da real: Processos, Contas a Receber (2 meses) e Despesas. */
	static byte[] planilhaModelo() {
		return new XlsxFicticio()
				.aba("Processos",
						l("Cliente", "Nº Processo", "Tipo de Ação", "Status", "Honorários R$", "Honorários (%)", "Valor da Causa", "Previsão de Recebimento"),
						l("ANA EXEMPLO DA SILVA", CNJ_MODELO, "PENSÃO ALIMENTÍCIA", "EM ANDAMENTO", 6000.0, null, 900.0, "R$ 1.200,00 DE ENTRADA + 24 VEZES DE R$ 200,00 A PARTIR DE ABRIL DE 2026"),
						l("BRUNO TESTE", null, "TRABALHISTA", "EM ANDAMENTO", null, 0.3, 20000.0, "AO FINAL DO PROCESSO"),
						l("CARLA MODELO", null, "ATIVIDADE ESTRANHA", "EM ANDAMENTO", "100 (TMP)", null, "XXX", "R$ 500,00 EM 2 PARCELAS"))
				.aba("Contas a Receber Abril 2026",
						l("ABRIL DE 2026"),
						l("Cliente", "Valor", "Data de Vencimento", "Período", "Status"),
						l("ANA EXEMPLO DA SILVA", 200.0, 46122.0, "PARCELA 1/24", "PAGO"),
						l("BRUNO TESTE", "R$400,00", "1ª QUARTA-FEIRA DO MÊS", "PARCELA 1 - ATÉ O FINAL DO PROCESSO", "PAGO METADE"),
						l("CARLA MODELO", 250.0, 46130.0, "PARCELA 1/2", "EM CANCELAMENTO"),
						l("DANIEL SEM CONTRATO", 300.0, 46131.0, "ENTRADA"),
						l(null, 750.0),
						l("LUCRO FINAL MENSAL", 750.0),
						l("EXTRA - DEFESA", 3500.0))
				.aba("Contas a Receber Maio 2026",
						l("MAIO DE 2026"),
						l("Cliente", "Valor", "Data de Vencimento", "Período", "Status"),
						l("ANA EXEMPLO DA SILVA", 200.0, 46152.0, "PARCELA 2/24", "PAGO"),
						l("ANA EXEMPLO DA SILVA", 200.0, 46153.0, "PARCELA 2/24", "EM ABERTO"))
				.aba("Despesas Mensais",
						l("DESPESAS DE MARÇO DE 2026"),
						l("Data", "Descrição", "Categoria", "Valor", "Forma de Pagamento"),
						l(46093.0, "6 TAÇAS", "LEMBRANÇA", 41.99, "PIX"),
						l(null, null, null, 41.99))
				.aba("Aba Estranha", l("x"))
				.bytes();
	}
}
