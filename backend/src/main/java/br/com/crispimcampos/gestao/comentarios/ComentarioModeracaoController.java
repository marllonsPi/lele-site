package br.com.crispimcampos.gestao.comentarios;

import br.com.crispimcampos.gestao.comentarios.ComentarioService.ComentarioInvalidoException;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fila de moderacao dos comentarios do site: somente socias. */
@RestController
@PreAuthorize("hasRole('SOCIA')")
class ComentarioModeracaoController {

	private final ComentarioService servico;

	ComentarioModeracaoController(ComentarioService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/comentarios")
	List<ComentarioService.Fila> fila(@RequestParam(required = false) String estado) {
		return servico.fila(estado);
	}

	@PostMapping("/api/comentarios/{id}/aprovar")
	Map<String, String> aprovar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return Map.of("estado", servico.decidirNoSistema(u.id(), id, true));
	}

	@PostMapping("/api/comentarios/{id}/rejeitar")
	Map<String, String> rejeitar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return Map.of("estado", servico.decidirNoSistema(u.id(), id, false));
	}

	@ExceptionHandler(ComentarioInvalidoException.class)
	ProblemDetail invalido(ComentarioInvalidoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}
}
