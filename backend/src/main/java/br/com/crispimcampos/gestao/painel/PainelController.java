package br.com.crispimcampos.gestao.painel;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('SOCIA')")
class PainelController {

	private final PainelService servico;

	PainelController(PainelService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/painel")
	PainelDto painel(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.painel(u);
	}

	@ExceptionHandler(AcessoNegadoException.class)
	ProblemDetail negado(AcessoNegadoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}
}
