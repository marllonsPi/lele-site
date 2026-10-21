package br.com.crispimcampos.gestao.cadastros;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Leitura filtrada por visibilidade para qualquer usuario; escrita so das socias. */
@RestController
class CadastroController {

	record DadosCliente(@NotBlank @Size(max = 200) String nome, @Size(max = 18) String documento,
			@Email @Size(max = 254) String email, @Size(max = 30) String telefone, @Size(max = 400) String endereco,
			@Size(max = 5000) String observacoes, @NotEmpty Set<AreaAtuacao> areas) {
	}

	record DadosProcesso(@NotBlank @Size(max = 30) String numero, @NotBlank @Size(max = 40) String tribunal,
			@Size(max = 200) String varaComarca, @Size(max = 200) String classe, @Size(max = 100) String situacao,
			@DecimalMin("0.00") BigDecimal valorCausa, @NotNull Long clienteId, @NotNull Long advogadoId,
			@NotNull AreaAtuacao area) {
	}

	record DadosParte(@NotBlank @Size(max = 200) String nome, @NotNull PapelParte papel) {
	}

	private final CadastroService servico;

	CadastroController(CadastroService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/clientes")
	List<ClienteDto> clientes(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(required = false) AreaAtuacao area) {
		return servico.listarClientes(u, area);
	}

	@GetMapping("/api/clientes/{id}")
	ClienteDto cliente(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.buscarCliente(u, id);
	}

	@PostMapping("/api/clientes")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('SOCIA')")
	ClienteDto criarCliente(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody DadosCliente d) {
		return servico.criarCliente(u.id(), d.nome(), d.documento(), d.email(), d.telefone(), d.endereco(), d.observacoes(),
				d.areas());
	}

	@PutMapping("/api/clientes/{id}")
	@PreAuthorize("hasRole('SOCIA')")
	ClienteDto atualizarCliente(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@Valid @RequestBody DadosCliente d) {
		return servico.atualizarCliente(u.id(), id, d.nome(), d.documento(), d.email(), d.telefone(), d.endereco(),
				d.observacoes(), d.areas());
	}

	@PostMapping("/api/clientes/{id}/desativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('SOCIA')")
	void desativarCliente(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		servico.desativarCliente(u.id(), id);
	}

	@PostMapping("/api/clientes/{id}/reativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('SOCIA')")
	void reativarCliente(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		servico.reativarCliente(u.id(), id);
	}

	@GetMapping("/api/processos")
	List<ProcessoDto> processos(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(required = false) Long clienteId,
			@RequestParam(required = false) AreaAtuacao area) {
		return servico.listarProcessos(u, clienteId, area);
	}

	@GetMapping("/api/processos/{id}")
	ProcessoDto processo(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.buscarProcesso(u, id);
	}

	@PostMapping("/api/processos")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('SOCIA')")
	ProcessoDto criarProcesso(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody DadosProcesso d) {
		return servico.criarProcesso(u.id(), d.numero(), d.tribunal(), d.varaComarca(), d.classe(), d.situacao(),
				d.valorCausa(), d.clienteId(), d.advogadoId(), d.area());
	}

	@PutMapping("/api/processos/{id}")
	@PreAuthorize("hasRole('SOCIA')")
	ProcessoDto atualizarProcesso(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@Valid @RequestBody DadosProcesso d) {
		return servico.atualizarProcesso(u.id(), id, d.tribunal(), d.varaComarca(), d.classe(), d.situacao(),
				d.valorCausa(), d.clienteId(), d.advogadoId(), d.area());
	}

	@PostMapping("/api/processos/{id}/desativar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('SOCIA')")
	void desativarProcesso(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		servico.desativarProcesso(u.id(), id);
	}

	@PostMapping("/api/processos/{id}/partes")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('SOCIA')")
	ProcessoDto adicionarParte(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@Valid @RequestBody DadosParte d) {
		return servico.adicionarParte(u.id(), id, d.nome(), d.papel());
	}

	@ExceptionHandler(RegraDeCadastroException.class)
	ProblemDetail regra(RegraDeCadastroException e) {
		return ProblemDetail.forStatusAndDetail(e.isConflito() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY,
				e.getMessage());
	}

	@ExceptionHandler(CadastroNaoEncontradoException.class)
	ProblemDetail naoEncontrado(CadastroNaoEncontradoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}
}
