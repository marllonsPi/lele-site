package br.com.crispimcampos.gestao.google;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cada pessoa gerencia somente a propria conexao: nenhum endpoint recebe id de usuario. */
@RestController
class GoogleController {

	private final GoogleContasService contas;

	GoogleController(GoogleContasService contas) {
		this.contas = contas;
	}

	@GetMapping("/api/google/status")
	GoogleContasService.Status status(@AuthenticationPrincipal UsuarioLogado eu) {
		return contas.status(eu.id());
	}

	@PostMapping("/api/google/conectar")
	Map<String, String> conectar(@AuthenticationPrincipal UsuarioLogado eu) {
		return Map.of("url", contas.iniciar(eu.id(), eu.email()));
	}

	/** URI registrada no Google. Volta para a tela de configuracoes com o resultado. */
	@GetMapping("/api/google/callback")
	ResponseEntity<Void> callback(@AuthenticationPrincipal UsuarioLogado eu, @RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error) {
		String resultado = "ok";
		if (error != null) {
			resultado = "negado";
		} else {
			try {
				contas.concluir(eu.id(), code, state);
			} catch (ConexaoGoogleException e) {
				resultado = "erro";
			}
		}
		return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create("/configuracoes?google=" + resultado).toString()).build();
	}

	@DeleteMapping("/api/google")
	Map<String, Boolean> desconectar(@AuthenticationPrincipal UsuarioLogado eu) {
		return Map.of("revogadoNoGoogle", contas.desconectar(eu.id()));
	}

	@ExceptionHandler(ConexaoGoogleException.class)
	ProblemDetail erro(ConexaoGoogleException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}
}
