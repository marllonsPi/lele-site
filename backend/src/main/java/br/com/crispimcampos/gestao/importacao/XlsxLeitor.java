package br.com.crispimcampos.gestao.importacao;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Leitor minimo de .xlsx (zip + XML): so texto e numeros brutos, sem formulas nem estilos. Datas chegam como numero serial do
 * Excel. Sem dependencia externa (a VM tem 1 GB). Protegido contra XXE (DTD desligado) e contra zip bomb (limite de bytes).
 */
public final class XlsxLeitor {

	/** Linha: numero (1-based) e celulas por coluna ("A", "B", ...), texto cru. Celula vazia nao aparece. */
	public record Linha(int numero, Map<String, String> celulas) {
		public String texto(String coluna) {
			String v = celulas.get(coluna);
			return v == null ? "" : v.trim();
		}
	}

	public record Aba(String nome, List<Linha> linhas) {
	}

	static final long LIMITE_BYTES = 60L * 1024 * 1024;

	private XlsxLeitor() {
	}

	public static List<Aba> ler(byte[] conteudo) {
		Map<String, byte[]> partes = new HashMap<>();
		long total = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(conteudo))) {
			ZipEntry e;
			while ((e = zip.getNextEntry()) != null) {
				String nome = e.getName();
				if (!nome.startsWith("xl/") || !nome.endsWith(".xml") && !nome.endsWith(".rels")) {
					continue;
				}
				byte[] dados = zip.readNBytes((int) Math.min(LIMITE_BYTES, Integer.MAX_VALUE));
				total += dados.length;
				if (total > LIMITE_BYTES) {
					throw new IllegalArgumentException("Planilha grande demais");
				}
				partes.put(nome, dados);
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Arquivo nao e uma planilha .xlsx valida");
		}
		if (!partes.containsKey("xl/workbook.xml")) {
			throw new IllegalArgumentException("Arquivo nao e uma planilha .xlsx valida");
		}
		try {
			List<String> textos = partes.containsKey("xl/sharedStrings.xml") ? textosCompartilhados(partes.get("xl/sharedStrings.xml"))
					: List.of();
			Map<String, String> rels = relacoes(partes.get("xl/_rels/workbook.xml.rels"));
			List<Aba> abas = new ArrayList<>();
			for (String[] ab : nomesDasAbas(partes.get("xl/workbook.xml"))) {
				String alvo = rels.get(ab[1]);
				if (alvo == null) {
					continue;
				}
				String caminho = alvo.startsWith("/") ? alvo.substring(1) : "xl/" + alvo;
				byte[] xml = partes.get(caminho);
				if (xml != null) {
					abas.add(new Aba(ab[0], linhas(xml, textos)));
				}
			}
			return abas;
		} catch (XMLStreamException e) {
			throw new IllegalArgumentException("Planilha corrompida");
		}
	}

	private static XMLStreamReader leitor(byte[] xml) throws XMLStreamException {
		XMLInputFactory f = XMLInputFactory.newFactory();
		f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		return f.createXMLStreamReader(new ByteArrayInputStream(xml));
	}

	private static List<String[]> nomesDasAbas(byte[] xml) throws XMLStreamException {
		List<String[]> r = new ArrayList<>();
		XMLStreamReader x = leitor(xml);
		while (x.hasNext()) {
			if (x.next() == XMLStreamConstants.START_ELEMENT && x.getLocalName().equals("sheet")) {
				String id = null;
				for (int i = 0; i < x.getAttributeCount(); i++) {
					if (x.getAttributeLocalName(i).equals("id")) {
						id = x.getAttributeValue(i);
					}
				}
				r.add(new String[] {x.getAttributeValue(null, "name"), id});
			}
		}
		return r;
	}

	private static Map<String, String> relacoes(byte[] xml) throws XMLStreamException {
		Map<String, String> m = new HashMap<>();
		if (xml == null) {
			return m;
		}
		XMLStreamReader x = leitor(xml);
		while (x.hasNext()) {
			if (x.next() == XMLStreamConstants.START_ELEMENT && x.getLocalName().equals("Relationship")) {
				m.put(x.getAttributeValue(null, "Id"), x.getAttributeValue(null, "Target"));
			}
		}
		return m;
	}

	private static List<String> textosCompartilhados(byte[] xml) throws XMLStreamException {
		List<String> r = new ArrayList<>();
		XMLStreamReader x = leitor(xml);
		StringBuilder atual = null;
		boolean emTexto = false;
		while (x.hasNext()) {
			int ev = x.next();
			if (ev == XMLStreamConstants.START_ELEMENT) {
				if (x.getLocalName().equals("si")) {
					atual = new StringBuilder();
				} else if (x.getLocalName().equals("t")) {
					emTexto = true;
				}
			} else if (ev == XMLStreamConstants.CHARACTERS && emTexto && atual != null) {
				atual.append(x.getText());
			} else if (ev == XMLStreamConstants.END_ELEMENT) {
				if (x.getLocalName().equals("t")) {
					emTexto = false;
				} else if (x.getLocalName().equals("si") && atual != null) {
					r.add(atual.toString());
					atual = null;
				}
			}
		}
		return r;
	}

	private static List<Linha> linhas(byte[] xml, List<String> textos) throws XMLStreamException {
		List<Linha> r = new ArrayList<>();
		XMLStreamReader x = leitor(xml);
		Map<String, String> celulas = null;
		int numero = 0;
		int proxima = 0;
		String ref = null;
		String tipo = null;
		StringBuilder valor = null;
		boolean lendoValor = false;
		while (x.hasNext()) {
			int ev = x.next();
			if (ev == XMLStreamConstants.START_ELEMENT) {
				switch (x.getLocalName()) {
					case "row" -> {
						String n = x.getAttributeValue(null, "r");
						numero = n == null ? proxima + 1 : Integer.parseInt(n);
						proxima = numero;
						celulas = new LinkedHashMap<>();
					}
					case "c" -> {
						ref = x.getAttributeValue(null, "r");
						tipo = x.getAttributeValue(null, "t");
						valor = new StringBuilder();
					}
					case "v", "t" -> lendoValor = valor != null;
					default -> {
					}
				}
			} else if (ev == XMLStreamConstants.CHARACTERS && lendoValor) {
				valor.append(x.getText());
			} else if (ev == XMLStreamConstants.END_ELEMENT) {
				switch (x.getLocalName()) {
					case "v", "t" -> lendoValor = false;
					case "c" -> {
						if (celulas != null && ref != null && valor != null && !valor.isEmpty()) {
							String v = valor.toString();
							if ("s".equals(tipo)) {
								int i = Integer.parseInt(v.trim());
								v = i < textos.size() ? textos.get(i) : "";
							}
							celulas.put(coluna(ref), v);
						}
						ref = null;
						valor = null;
					}
					case "row" -> {
						if (celulas != null && !celulas.isEmpty()) {
							r.add(new Linha(numero, celulas));
						}
						celulas = null;
					}
					default -> {
					}
				}
			}
		}
		return r;
	}

	private static String coluna(String ref) {
		int i = 0;
		while (i < ref.length() && Character.isLetter(ref.charAt(i))) {
			i++;
		}
		return ref.substring(0, i);
	}

	public static List<Aba> ler(InputStream in) throws IOException {
		return ler(in.readNBytes((int) LIMITE_BYTES));
	}
}
