package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.cadastros.Documento;
import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai dados de texto de contrato/procuracao/documentos: CPF/CNPJ (com digito verificador), RG, estado civil, endereco,
 * telefone, e-mail, valor, forma de pagamento, parcelas, data de assinatura e numeros de processo (CNJ valido). Melhor esforco:
 * o que nao for achado fica nulo e a previa marca "extraido automaticamente, conferir"; nada e inventado.
 */
final class ExtratorDocumento {

	record Dados(String documento, String rg, String estadoCivil, String endereco, String telefone, String email, BigDecimal valorTotal,
			String formaPagamento, Integer parcelas, BigDecimal valorParcela, BigDecimal entrada, LocalDate dataAssinatura, List<String> processos) {
	}

	private static final Pattern CPF_CNPJ = Pattern.compile("(?<!\\d)(\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}|\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2})(?!\\d)");
	private static final Pattern CNJ = Pattern.compile("(?<!\\d)(\\d{7})-?(\\d{2})\\.?(\\d{4})\\.?(\\d)\\.?(\\d{2})\\.?(\\d{4})(?!\\d)");
	private static final Pattern RG = Pattern.compile("(?i)(?:RG|carteira de identidade|identidade)\\s*(?:n[ºo°.]*)?\\s*[:.]?\\s*([0-9][0-9.\\-xX]{4,14})");
	private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
	private static final Pattern TELEFONE = Pattern.compile("(?:\\(?\\d{2}\\)?\\s?)?9?\\d{4}[-\\s]?\\d{4}");
	private static final Pattern ESTADO_CIVIL = Pattern.compile("(?i)\\b(solteir[oa]|casad[oa]|divorciad[oa]|vi[úu]v[oa]|separad[oa]( judicialmente)?|uni[ãa]o est[áa]vel)\\b");
	private static final Pattern ENDERECO = Pattern.compile("(?i)(?:residente e domiciliad[oa]|domiciliad[oa]|resid[ae]nte|endere[çc]o)\\s*(?:na|no|em|:)?\\s*([^;\\n]{10,160}?)(?:,?\\s*(?:inscrit|portador|CEP|e-?mail|telefone|doravante|neste ato)|[;\\n]|$)");
	private static final Pattern VALOR = Pattern.compile("(?i)R\\$\\s*([\\d.]+,\\d{2})");
	private static final Pattern PARCELAS = Pattern.compile("(?i)(\\d{1,3})\\s*\\([^)]*\\)\\s*parcelas|(\\d{1,3})\\s*(?:parcelas|presta[çc][õo]es)");
	private static final Pattern DATA_EXTENSO = Pattern.compile("(?i)(\\d{1,2})\\s*de\\s*(janeiro|fevereiro|mar[çc]o|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\s*de\\s*(\\d{4})");
	private static final Pattern DATA_NUMERICA = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");
	private static final List<String> MESES = List.of("janeiro", "fevereiro", "marco", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro");

	private ExtratorDocumento() {
	}

	static Dados extrair(String texto) {
		if (texto == null || texto.isBlank()) {
			return new Dados(null, null, null, null, null, null, null, null, null, null, null, null, List.of());
		}
		String t = texto.replaceAll("[ \\t\\u00a0]+", " ");
		String c = secaoDoCliente(t); // dados pessoais so da parte contratante/outorgante (nao do escritorio nem da parte contraria)
		return new Dados(documento(c), primeiro(RG, c), estadoCivil(c), endereco(c), telefone(c), primeiro(EMAIL, c, 0), valorTotal(t), forma(t), parcelas(t),
				valorParcela(t), entrada(t), dataAssinatura(t), processos(t));
	}

	/** Trecho da qualificacao do cliente: de CONTRATANTE/OUTORGANTE ate a qualificacao da outra parte (ou 1500 caracteres). */
	static String secaoDoCliente(String t) {
		Matcher ini = Pattern.compile("(?i)\\b(contratante|outorgante)\\b").matcher(t);
		if (!ini.find()) {
			return t;
		}
		int de = ini.start();
		Matcher fim = Pattern.compile("(?i)\\b(contratad[oa]s?|outorgad[oa]s?)\\b").matcher(t);
		int ate = Math.min(t.length(), de + 1500);
		while (fim.find()) {
			if (fim.start() > de + 40) {
				ate = Math.min(ate, fim.start());
				break;
			}
		}
		return t.substring(de, ate);
	}

	/** Primeiro CPF/CNPJ com digito verificador valido. */
	static String documento(String t) {
		Matcher m = CPF_CNPJ.matcher(t);
		while (m.find()) {
			String d = Documento.normalizar(m.group(1));
			if (Documento.valido(d)) {
				return d;
			}
		}
		return null;
	}

	/** Todos os numeros CNJ validos, na ordem em que aparecem, sem repeticao. */
	static List<String> processos(String t) {
		Set<String> r = new LinkedHashSet<>();
		Matcher m = CNJ.matcher(t);
		while (m.find()) {
			String n = m.group(1) + m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6);
			if (NumeroCnj.valido(n)) {
				r.add(n);
			}
		}
		return new ArrayList<>(r);
	}

	private static String estadoCivil(String t) {
		Matcher m = ESTADO_CIVIL.matcher(t);
		return m.find() ? m.group(1).toLowerCase().replace("viúv", "viuv").replace("viuv", "viúv").replace("união", "uniao").replace("estável", "estavel") : null;
	}

	private static String endereco(String t) {
		Matcher m = ENDERECO.matcher(t);
		return m.find() ? m.group(1).trim() : null;
	}

	private static String telefone(String t) {
		Matcher m = TELEFONE.matcher(t);
		while (m.find()) {
			String s = m.group().trim();
			String digitos = s.replaceAll("\\D", "");
			if ((digitos.length() == 10 || digitos.length() == 11) && !CPF_CNPJ.matcher(s).matches()) {
				return s;
			}
		}
		return null;
	}

	private static String primeiro(Pattern p, String t) {
		return primeiro(p, t, 1);
	}

	private static String primeiro(Pattern p, String t, int grupo) {
		Matcher m = p.matcher(t);
		return m.find() ? m.group(grupo).trim().replaceAll("[.,;]+$", "") : null;
	}

	/** Valor total: o maior valor em R$ perto de "honorarios"/"valor total"; senao o maior valor citado. */
	private static BigDecimal valorTotal(String t) {
		Matcher m = Pattern.compile("(?i)(?:honor[áa]rios[^.]{0,120}?|valor total[^.]{0,60}?|totalizando[^.]{0,40}?)R\\$\\s*([\\d.]+,\\d{2})").matcher(t);
		if (m.find()) {
			return Texto.valor(m.group(1));
		}
		BigDecimal maior = null;
		Matcher v = VALOR.matcher(t);
		while (v.find()) {
			BigDecimal x = Texto.valor(v.group(1));
			if (x != null && (maior == null || x.compareTo(maior) > 0)) {
				maior = x;
			}
		}
		return maior;
	}

	private static Integer parcelas(String t) {
		Matcher m = PARCELAS.matcher(t);
		if (m.find()) {
			return Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
		}
		return null;
	}

	private static BigDecimal valorParcela(String t) {
		Matcher m = Pattern.compile("(?i)parcelas?\\s+(?:mensais\\s+)?(?:e\\s+)?(?:sucessivas\\s+)?de\\s+R\\$\\s*([\\d.]+,\\d{2})").matcher(t);
		return m.find() ? Texto.valor(m.group(1)) : null;
	}

	private static BigDecimal entrada(String t) {
		Matcher m = Pattern.compile("(?i)entrada\\s+(?:de\\s+)?R\\$\\s*([\\d.]+,\\d{2})").matcher(t);
		return m.find() ? Texto.valor(m.group(1)) : null;
	}

	/** Trecho do contrato sobre pagamento (ate 300 caracteres), para o cadastro. */
	private static String forma(String t) {
		Matcher m = Pattern.compile("(?i)(?:forma de pagamento|pagamento|honor[áa]rios)[^\\n]{0,300}").matcher(t);
		String achado = null;
		while (m.find()) {
			if (m.group().contains("R$") && (achado == null || m.group().toLowerCase().contains("parcela") || m.group().toLowerCase().contains("vista"))) {
				achado = m.group();
				if (achado.toLowerCase().contains("parcela") || achado.toLowerCase().contains("vista")) {
					break;
				}
			}
		}
		return achado == null ? null : achado.trim().substring(0, Math.min(achado.trim().length(), 300));
	}

	/** Data de assinatura: a ultima data do documento (por extenso ou dd/mm/aaaa), que costuma ser a do fecho; null se nao houver. */
	private static LocalDate dataAssinatura(String t) {
		LocalDate ultima = null;
		Matcher e = DATA_EXTENSO.matcher(t);
		while (e.find()) {
			int mes = MESES.indexOf(Texto.normalizar(e.group(2)).toLowerCase()) + 1;
			ultima = montar(Integer.parseInt(e.group(3)), mes, Integer.parseInt(e.group(1)), ultima);
		}
		Matcher n = DATA_NUMERICA.matcher(t);
		while (n.find()) {
			ultima = montar(Integer.parseInt(n.group(3)), Integer.parseInt(n.group(2)), Integer.parseInt(n.group(1)), ultima);
		}
		return ultima;
	}

	private static LocalDate montar(int ano, int mes, int dia, LocalDate padrao) {
		try {
			LocalDate d = LocalDate.of(ano, mes, dia);
			return d.getYear() >= 2000 && d.getYear() <= LocalDate.now().getYear() + 1 ? d : padrao;
		} catch (java.time.DateTimeException ex) {
			return padrao;
		}
	}
}
