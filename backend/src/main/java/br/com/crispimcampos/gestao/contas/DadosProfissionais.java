package br.com.crispimcampos.gestao.contas;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Dados de contato e atuacao de uma conta (todos opcionais). {@code alertasEmail} nulo mantem o padrao (ligado);
 * {@code emailAlertas} vazio usa o e-mail de login.
 */
public record DadosProfissionais(@Size(max = 30) String telefone, @Size(max = 30) String whatsapp,
		@Size(max = 80) String cargo, @Email @Size(max = 254) String emailAlertas, Boolean alertasEmail,
		@Size(max = 500) String assinatura) {

	public static DadosProfissionais vazio() {
		return new DadosProfissionais(null, null, null, null, null, null);
	}
}
