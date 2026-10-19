package br.com.crispimcampos.gestao.contas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cada usuario gerencia o proprio segundo fator. */
@RestController
class DoisFatoresController {

	record Codigo(@NotBlank String codigo) {
	}

	private final DoisFatoresService servico;

	DoisFatoresController(DoisFatoresService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/me/2fa")
	Map<String, Boolean> status(@AuthenticationPrincipal UsuarioLogado u) {
		return Map.of("ativo", servico.ativo(u.id()));
	}

	@PostMapping("/api/me/2fa/iniciar")
	DoisFatoresService.Cadastro iniciar(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.iniciar(u.id());
	}

	@PostMapping("/api/me/2fa/confirmar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirmar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody Codigo c) {
		servico.confirmar(u.id(), c.codigo());
	}

	@PostMapping("/api/me/2fa/desativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody Codigo c) {
		servico.desativar(u.id(), c.codigo());
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	ProblemDetail invalido(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}
}
