package br.com.crispimcampos.gestao.alertas;

/** Saida de e-mail. Sem implementacao configurada, as notificacoes ficam PENDENTE e sao enviadas quando houver SMTP. */
public interface EnviadorEmail {

	/** @throws RuntimeException se o envio falhar */
	void enviar(String para, String assunto, String corpo);

	/** Com anexo (relatorios em PDF). Implementacoes sem suporte devem lancar excecao em vez de ignorar o anexo. */
	default void enviarComAnexo(String para, String assunto, String corpo, String nomeArquivo, byte[] anexo) {
		throw new UnsupportedOperationException("Envio com anexo nao suportado por este provedor");
	}
}
