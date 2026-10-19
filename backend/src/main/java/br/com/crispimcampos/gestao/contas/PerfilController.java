package br.com.crispimcampos.gestao.contas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cada pessoa cuida do proprio contato, das preferencias de alerta e da propria senha. */
@RestController
class PerfilController {

	record TrocaDeSenha(@NotBlank String senhaAtual, @NotBlank String novaSenha) {
	}

	private final UsuarioService servico;

	PerfilController(UsuarioService servico) {
		this.servico = servico;
	}

	record NomeDeUsuario(Long id, String nome, Perfil perfil, boolean ativo) {
	}

	/** Nomes da equipe para qualquer perfil exibir "quem" nas telas; so dados de identificacao, sem contato. */
	@GetMapping("/api/usuarios/nomes")
	List<NomeDeUsuario> nomes() {
		return servico.listar().stream().map(u -> new NomeDeUsuario(u.id(), u.nome(), u.perfil(), u.ativo())).toList();
	}

	@GetMapping("/api/me/perfil")
	UsuarioDto perfil(@AuthenticationPrincipal UsuarioLogado eu) {
		return servico.buscar(eu.id());
	}

	@PutMapping("/api/me/perfil")
	UsuarioDto atualizar(@AuthenticationPrincipal UsuarioLogado eu, @Valid @RequestBody DadosProfissionais dados) {
		return servico.atualizarMeuPerfil(eu.id(), dados);
	}

	@PutMapping("/api/me/senha")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void trocarSenha(@AuthenticationPrincipal UsuarioLogado eu, @Valid @RequestBody TrocaDeSenha c) {
		servico.trocarMinhaSenha(eu.id(), c.senhaAtual(), c.novaSenha());
	}

	@ExceptionHandler(RegraDeContaException.class)
	ProblemDetail regra(RegraDeContaException e) {
		return ProblemDetail.forStatusAndDetail(e.isConflito() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}
}
