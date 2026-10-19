package br.com.crispimcampos.gestao.alertas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envio por SMTP quando {@code spring.mail.host} esta configurado (provedor transacional: pendencia 13). */
@Component
@ConditionalOnProperty("spring.mail.host")
class EnviadorEmailSmtp implements EnviadorEmail {

	private final JavaMailSender remetente;
	private final String de;

	EnviadorEmailSmtp(JavaMailSender remetente, @Value("${app.alertas.remetente:nao-responda@localhost}") String de) {
		this.remetente = remetente;
		this.de = de;
	}

	@Override
	public void enviar(String para, String assunto, String corpo) {
		SimpleMailMessage m = new SimpleMailMessage();
		m.setFrom(de);
		m.setTo(para);
		m.setSubject("[Crispim & Campos] " + assunto);
		m.setText(corpo);
		remetente.send(m);
	}
}
