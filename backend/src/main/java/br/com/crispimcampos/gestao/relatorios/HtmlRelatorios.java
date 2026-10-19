package br.com.crispimcampos.gestao.relatorios;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Monta o XHTML dos relatorios (com timbre) para o PDF. Todo texto dinamico passa por {@link #esc}. */
final class HtmlRelatorios {

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private HtmlRelatorios() {
	}

	record Timbre(String nome, String endereco, String contato, String logoBase64) {
	}

	static String esc(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				// caracteres fora do Latin-1 nao existem nas fontes padrao do PDF
				.replaceAll("[^\\u0009\\u000A\\u000D\\u0020-\\u007E\\u00A0-\\u00FF]", "-");
	}

	static String data(LocalDate d) {
		return d == null ? "" : d.format(DATA);
	}

	static String moeda(BigDecimal v) {
		return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(v).replace(' ', ' ');
	}

	private static String cabecalho(Timbre t, String titulo, String subtitulo) {
		StringBuilder b = new StringBuilder();
		b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/><style>")
				.append("@page{size:A4;margin:22mm 16mm 20mm 16mm}body{font-family:sans-serif;font-size:10.5pt;color:#222}")
				.append("h1{font-size:16pt;color:#6b5420;margin:4mm 0 1mm}h2{font-size:12.5pt;color:#6b5420;border-bottom:1px solid #c9b27a;padding-bottom:1mm;margin-top:7mm}")
				.append(".timbre{border-bottom:2px solid #c9b27a;padding-bottom:3mm}.timbre img{height:16mm}.timbre .nome{font-size:13pt;font-weight:bold;color:#6b5420}")
				.append(".peq{font-size:8.5pt;color:#666}table{width:100%;border-collapse:collapse;margin-top:2mm}th,td{border:1px solid #ccc;padding:1.5mm 2mm;text-align:left;font-size:9pt;vertical-align:top}")
				.append("th{background:#f1ead8}.caixa{background:#faf7ee;border:1px solid #e0d4b0;padding:3mm;margin-top:3mm}")
				.append("</style></head><body><div class=\"timbre\">");
		if (t.logoBase64() != null && !t.logoBase64().isEmpty()) {
			b.append("<img src=\"data:image/png;base64,").append(t.logoBase64()).append("\"/><br/>");
		}
		b.append("<span class=\"nome\">").append(esc(t.nome())).append("</span>");
		if (!t.endereco().isBlank() || !t.contato().isBlank()) {
			b.append("<br/><span class=\"peq\">").append(esc(t.endereco())).append(" ").append(esc(t.contato())).append("</span>");
		}
		b.append("</div><h1>").append(esc(titulo)).append("</h1>");
		if (subtitulo != null && !subtitulo.isBlank()) {
			b.append("<div class=\"peq\">").append(esc(subtitulo)).append("</div>");
		}
		return b.toString();
	}

	/** Relatorio resumido ao cliente (item 14.2). */
	static String cliente(Timbre t, ConteudoCliente c) {
		StringBuilder b = new StringBuilder(cabecalho(t, "Relatorio de andamento processual",
				"Cliente: " + c.clienteNome() + " | Periodo: " + (c.periodoInicio() == null ? "todo o historico" : data(c.periodoInicio())) + " a " + data(c.periodoFim())));
		if (c.comentarios() != null && !c.comentarios().isBlank()) {
			b.append("<div class=\"caixa\"><b>Comentarios do advogado</b><br/>").append(esc(c.comentarios()).replace("\n", "<br/>")).append("</div>");
		}
		for (ConteudoCliente.ProcessoRel p : c.processos()) {
			b.append("<h2>Processo ").append(esc(p.numero())).append("</h2><table>")
					.append(linha("Vara / comarca", p.varaComarca())).append(linha("Tribunal", p.tribunal()))
					.append(linha("Tipo de acao", p.tipoAcao())).append(linha("Situacao atual", p.situacao()))
					.append(linha("Partes", String.join("; ", p.partes()))).append("</table>");
			b.append(secao("Movimentacoes do periodo", p.movimentacoes(), "Nenhuma movimentacao registrada no periodo."));
			b.append(secao("O que o escritorio fez", p.feitos(), "Nenhuma providencia concluida no periodo."));
			b.append(secao("Proximos passos", p.proximosPassos(), "Nenhum compromisso ou providencia prevista no momento."));
		}
		if (c.financeiro() != null) {
			b.append("<h2>Resumo financeiro</h2><table>").append(linha("Parcelas pagas", moeda(c.financeiro().parcelasPagas())))
					.append(linha("Parcelas a vencer", moeda(c.financeiro().parcelasAVencer()))).append("</table>");
		}
		return b.append("</body></html>").toString();
	}

	private static String linha(String k, String v) {
		return "<tr><th style=\"width:32%\">" + esc(k) + "</th><td>" + esc(v) + "</td></tr>";
	}

	private static String secao(String titulo, List<ConteudoCliente.Item> itens, String vazio) {
		StringBuilder b = new StringBuilder("<h2>" + esc(titulo) + "</h2>");
		if (itens.isEmpty()) {
			return b.append("<p class=\"peq\">").append(esc(vazio)).append("</p>").toString();
		}
		b.append("<table>");
		for (ConteudoCliente.Item i : itens) {
			b.append("<tr><td style=\"width:18%\">").append(data(i.data())).append("</td><td>").append(esc(i.texto())).append("</td></tr>");
		}
		return b.append("</table>").toString();
	}

	/** Relatorios internos (item 14.1): tabela generica. */
	static String interno(Timbre t, RelatorioDados d) {
		StringBuilder b = new StringBuilder(cabecalho(t, d.titulo(), d.subtitulo() + " | Gerado em " + d.geradoEm()));
		b.append("<table><tr>");
		d.colunas().forEach(c -> b.append("<th>").append(esc(c)).append("</th>"));
		b.append("</tr>");
		if (d.linhas().isEmpty()) {
			b.append("<tr><td colspan=\"").append(Math.max(1, d.colunas().size())).append("\">Nenhum registro.</td></tr>");
		}
		for (List<String> l : d.linhas()) {
			b.append("<tr>");
			l.forEach(c -> b.append("<td>").append(esc(c)).append("</td>"));
			b.append("</tr>");
		}
		return b.append("</table></body></html>").toString();
	}
}
