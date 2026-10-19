package br.com.crispimcampos.gestao.seguranca;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Registra logins. Falha de login nao grava o e-mail digitado (dado pessoal). */
@Component
class AuditoriaDeAcesso {

	private final AuditoriaService auditoria;

	AuditoriaDeAcesso(AuditoriaService auditoria) {
		this.auditoria = auditoria;
	}

	@EventListener
	void sucesso(AuthenticationSuccessEvent evento) {
		if (evento.getAuthentication().getPrincipal() instanceof UsuarioLogado u) {
			auditoria.registrar(u.id(), "LOGIN", "usuario", String.valueOf(u.id()), null);
		}
	}

	@EventListener
	void falha(AbstractAuthenticationFailureEvent evento) {
		auditoria.registrar(null, "LOGIN_FALHA", null, null, evento.getException().getClass().getSimpleName());
	}
}
