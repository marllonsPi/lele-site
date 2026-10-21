package br.com.crispimcampos.gestao.contas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestao de usuarios e OABs: exclusiva das socias (item 1). */
@RestController
@PreAuthorize("hasRole('SOCIA')")
class UsuarioController {

	record NovoUsuario(@NotBlank @Size(max = 200) String nome, @NotBlank @Email @Size(max = 254) String email,
			@NotBlank String senha, @NotNull Perfil perfil, Long supervisorId, @Valid DadosProfissionais profissionais,
			Boolean mestre) {
	}

	record EdicaoUsuario(@NotBlank @Size(max = 200) String nome, @NotBlank @Email @Size(max = 254) String email,
			@NotNull Perfil perfil, Long supervisorId, @Valid DadosProfissionais profissionais, Boolean mestre) {
	}

	record NovaSenha(@NotBlank String senha) {
	}

	record NovaOab(@NotBlank @Size(max = 20) String numero, @NotBlank @Size(min = 2, max = 2) String uf,
			boolean principal) {
	}

	private final UsuarioService servico;

	UsuarioController(UsuarioService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/usuarios")
	List<UsuarioDto> listar(@AuthenticationPrincipal UsuarioLogado ator, @RequestParam(defaultValue = "false") boolean incluirMestre) {
		// a conta de suporte so aparece para ela mesma, e apenas quando pedida (tela de administracao de contas)
		return incluirMestre && ator.mestre() ? servico.listarComMestre() : servico.listar();
	}

	@GetMapping("/api/usuarios/{id}")
	UsuarioDto buscar(@PathVariable Long id) {
		return servico.buscar(id);
	}

	@PostMapping("/api/usuarios")
	@ResponseStatus(HttpStatus.CREATED)
	UsuarioDto criar(@AuthenticationPrincipal UsuarioLogado ator, @Valid @RequestBody NovoUsuario c) {
		return servico.criar(ator.id(), c.nome(), c.email(), c.senha(), c.perfil(), c.supervisorId(), c.profissionais(),
				Boolean.TRUE.equals(c.mestre()));
	}

	@PutMapping("/api/usuarios/{id}")
	UsuarioDto atualizar(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long id,
			@Valid @RequestBody EdicaoUsuario c) {
		boolean mestre = c.mestre() != null ? c.mestre() : servico.buscar(id).mestre();
		return servico.atualizar(ator.id(), id, c.nome(), c.email(), c.perfil(), c.supervisorId(), c.profissionais(), mestre);
	}

	@PostMapping("/api/usuarios/{id}/desativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativar(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long id) {
		servico.desativar(ator.id(), id);
	}

	@PostMapping("/api/usuarios/{id}/reativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reativar(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long id) {
		servico.reativar(ator.id(), id);
	}

	@PutMapping("/api/usuarios/{id}/senha")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void redefinirSenha(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long id,
			@Valid @RequestBody NovaSenha c) {
		servico.redefinirSenha(ator.id(), id, c.senha());
	}

	@PostMapping("/api/usuarios/{id}/oabs")
	@ResponseStatus(HttpStatus.CREATED)
	OabDto adicionarOab(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long id,
			@Valid @RequestBody NovaOab c) {
		return servico.adicionarOab(ator.id(), id, c.numero(), c.uf(), c.principal());
	}

	@DeleteMapping("/api/oabs/{oabId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativarOab(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long oabId) {
		servico.desativarOab(ator.id(), oabId);
	}

	@PostMapping("/api/oabs/{oabId}/reativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void reativarOab(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long oabId) {
		servico.reativarOab(ator.id(), oabId);
	}

	@PostMapping("/api/oabs/{oabId}/principal")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void tornarPrincipal(@AuthenticationPrincipal UsuarioLogado ator, @PathVariable Long oabId) {
		servico.tornarPrincipal(ator.id(), oabId);
	}

	@ExceptionHandler(RegraDeContaException.class)
	ProblemDetail regra(RegraDeContaException e) {
		HttpStatus status = e.isConflito() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
		return ProblemDetail.forStatusAndDetail(status, e.getMessage());
	}

	@ExceptionHandler(RecursoNaoEncontradoException.class)
	ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}
}
