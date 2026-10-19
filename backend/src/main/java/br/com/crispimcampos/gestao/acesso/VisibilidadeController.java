package br.com.crispimcampos.gestao.acesso;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Matriz de visibilidade configuravel pelas socias, por usuario. */
@RestController
@PreAuthorize("hasRole('SOCIA')")
class VisibilidadeController {

	private final MatrizVisibilidadeService matriz;

	VisibilidadeController(MatrizVisibilidadeService matriz) {
		this.matriz = matriz;
	}

	@GetMapping("/api/visibilidade")
	List<ParVisibilidade> listar() {
		return matriz.listar();
	}

	@PutMapping("/api/visibilidade/{observadorId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void definirLinha(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long observadorId,
			@RequestBody Set<ParVisibilidade> regras) {
		matriz.definirLinha(ator.id(), observadorId, regras);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalido(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}
}
