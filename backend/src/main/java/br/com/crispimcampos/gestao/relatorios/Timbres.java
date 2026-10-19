package br.com.crispimcampos.gestao.relatorios;

import br.com.crispimcampos.gestao.sistema.ConfiguracaoService;
import java.io.IOException;
import java.util.Base64;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Dados do escritorio para o timbre; vem da configuracao editavel pelo acesso mestre (pendencia 10). */
@Component
class Timbres {

	private final ConfiguracaoService configuracao;
	private final String logo;

	Timbres(ConfiguracaoService configuracao) {
		this.configuracao = configuracao;
		String l = null;
		try (var in = new ClassPathResource("timbre/logo.png").getInputStream()) {
			l = Base64.getEncoder().encodeToString(in.readAllBytes());
		} catch (IOException e) {
			// sem logotipo o timbre sai so com o nome
		}
		this.logo = l;
	}

	HtmlRelatorios.Timbre timbre() {
		String contato = configuracao.valor(ConfiguracaoService.ESCRITORIO_CONTATO, "");
		String email = configuracao.valor("escritorio.email", "");
		if (!email.isBlank()) {
			contato = contato.isBlank() ? email : contato + " · " + email;
		}
		return new HtmlRelatorios.Timbre(configuracao.valor(ConfiguracaoService.ESCRITORIO_NOME, "Crispim & Campos Advocacia"),
				configuracao.valor(ConfiguracaoService.ESCRITORIO_ENDERECO, ""), contato, logo);
	}
}
