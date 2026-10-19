package br.com.crispimcampos.gestao.seguranca;

import org.springframework.security.core.AuthenticationException;

/** Senha correta, mas o usuario tem 2FA ativo e nao enviou o codigo: a SPA pede o codigo e repete o login. */
class DoisFatoresObrigatorioException extends AuthenticationException {

	DoisFatoresObrigatorioException() {
		super("Codigo do segundo fator obrigatorio");
	}
}
