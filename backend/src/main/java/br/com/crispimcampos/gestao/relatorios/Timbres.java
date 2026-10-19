package br.com.crispimcampos.gestao.relatorios;

import java.io.IOException;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Dados do escritorio para o timbre (pendencia 10: endereco e contato vem de configuracao). */
@Component
class Timbres {

	private final HtmlRelatorios.Timbre timbre;

	Timbres(@Value("${app.escritorio.nome:Crispim & Campos Advocacia}") String nome,
			@Value("${app.escritorio.endereco:}") String endereco, @Value("${app.escritorio.contato:}") String contato) {
		String logo = null;
		try (var in = new ClassPathResource("timbre/logo.png").getInputStream()) {
			logo = Base64.getEncoder().encodeToString(in.readAllBytes());
		} catch (IOException e) {
			// sem logotipo o timbre sai so com o nome
		}
		this.timbre = new HtmlRelatorios.Timbre(nome, endereco, contato, logo);
	}

	HtmlRelatorios.Timbre timbre() {
		return timbre;
	}
}
