package br.com.crispimcampos.gestao.lgpd;

import br.com.crispimcampos.gestao.cadastros.CadastroNaoEncontradoException;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('SOCIA')")
class LgpdController {

	private final LgpdService servico;

	LgpdController(LgpdService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/lgpd/clientes/{id}/exportar")
	Map<String, Object> exportar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.exportar(u, id);
	}

	@PostMapping("/api/lgpd/clientes/{id}/anonimizar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void anonimizar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		servico.anonimizar(u, id);
	}

	@ExceptionHandler(CadastroNaoEncontradoException.class)
	ProblemDetail naoEncontrado(CadastroNaoEncontradoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(RuntimeException.class)
	ProblemDetail regra(RuntimeException e) {
		if (e instanceof SecurityException) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
		}
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}
}
