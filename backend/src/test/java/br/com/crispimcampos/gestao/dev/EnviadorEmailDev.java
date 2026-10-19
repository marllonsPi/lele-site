package br.com.crispimcampos.gestao.dev;

import br.com.crispimcampos.gestao.alertas.EnviadorEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Em desenvolvimento nao sai e-mail de verdade: so registra no log (enderecos *.test, dados ficticios). */
@Component
@Profile("dev")
class EnviadorEmailDev implements EnviadorEmail {

	private static final Logger log = LoggerFactory.getLogger(EnviadorEmailDev.class);

	@Override
	public void enviar(String para, String assunto, String corpo) {
		log.info("[DEV] e-mail simulado para {}: {}", para, assunto);
	}
}
