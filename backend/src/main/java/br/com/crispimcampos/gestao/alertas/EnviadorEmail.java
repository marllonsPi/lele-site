package br.com.crispimcampos.gestao.alertas;

/** Saida de e-mail. Sem implementacao configurada, as notificacoes ficam PENDENTE e sao enviadas quando houver SMTP. */
public interface EnviadorEmail {

	/** @throws RuntimeException se o envio falhar */
	void enviar(String para, String assunto, String corpo);
}
