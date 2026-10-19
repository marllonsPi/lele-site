package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import br.com.crispimcampos.gestao.google.GoogleContasService;
import br.com.crispimcampos.gestao.google.MensagemGmail;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Captura por e-mail: le o Gmail da propria pessoa (conta conectada por OAuth) apenas nas mensagens que casam com a
 * consulta configurada (padrao: remetentes de dominio .jus.br no periodo). O titular da publicacao e quem conectou a
 * conta, entao a visibilidade segue a regra normal (invariante 1). Idempotente pelo id da mensagem (invariante 4).
 */
@Component
class GmailFonte implements FonteDePublicacoes {

	static final int MAXIMO_MENSAGENS = 200;
	static final int MAXIMO_TEXTO = 20_000;
	private static final Pattern CNJ = Pattern.compile("\\b\\d{7}-\\d{2}\\.\\d{4}\\.\\d\\.\\d{2}\\.\\d{4}\\b");

	private final GoogleContasService google;
	private final String consultaBase;

	GmailFonte(GoogleContasService google, @Value("${app.google.gmail.query:from:jus.br}") String consultaBase) {
		this.google = google;
		this.consultaBase = consultaBase;
	}

	@Override
	public Fonte fonte() {
		return Fonte.EMAIL;
	}

	@Override
	public List<Long> titularesProprios() {
		return google.usuariosConectados();
	}

	@Override
	public List<DadosBrutos> buscar(OabMonitorada oab, LocalDate de, LocalDate ate) {
		// Gmail: "after" inclusivo e "before" exclusivo
		String consulta = consultaBase + " after:" + de.toString().replace('-', '/') + " before:" + ate.plusDays(1).toString().replace('-', '/');
		return google.mensagens(oab.titularId(), consulta, MAXIMO_MENSAGENS).stream().map(GmailFonte::converter).toList();
	}

	static DadosBrutos converter(MensagemGmail m) {
		String conteudo = "Assunto: " + m.assunto() + "\n\n" + m.texto();
		if (conteudo.length() > MAXIMO_TEXTO) {
			conteudo = conteudo.substring(0, MAXIMO_TEXTO);
		}
		return new DadosBrutos(Fonte.EMAIL, m.id(), numeroCnj(m.assunto() + "\n" + m.texto()), null, m.data(), conteudo,
				Hashes.sha256(conteudo));
	}

	/** Primeiro numero CNJ valido (digito verificador conferido) do texto; sem ele a publicacao entra por hash. */
	static String numeroCnj(String texto) {
		Matcher m = CNJ.matcher(texto == null ? "" : texto);
		while (m.find()) {
			if (NumeroCnj.valido(m.group())) {
				return m.group();
			}
		}
		return null;
	}
}
