package br.com.crispimcampos.gestao.agenda;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros de regra da agenda para todos os controllers do modulo. */
@RestControllerAdvice(basePackageClasses = AgendaErros.class)
class AgendaErros {

	@ExceptionHandler(RegraDeAgendaException.class)
	ProblemDetail regra(RegraDeAgendaException e) {
		return ProblemDetail.forStatusAndDetail(e.isProibido() ? HttpStatus.FORBIDDEN : HttpStatus.UNPROCESSABLE_ENTITY,
				e.getMessage());
	}

	@ExceptionHandler(br.com.crispimcampos.gestao.cadastros.CadastroNaoEncontradoException.class)
	ProblemDetail processoNaoEncontrado(br.com.crispimcampos.gestao.cadastros.CadastroNaoEncontradoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(AgendaNaoEncontradaException.class)
	ProblemDetail naoEncontrado(AgendaNaoEncontradaException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}
}
