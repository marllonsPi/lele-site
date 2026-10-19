package br.com.crispimcampos.gestao.atendimento;

import br.com.crispimcampos.gestao.cadastros.CadastroNaoEncontradoException;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AtendimentoController {

	record NovoAtendimento(@NotNull Long clienteId, Long processoId, @NotBlank String canal, LocalDateTime momento,
			@NotBlank String resumo, Long atendenteId) {
	}

	record NovoAndamento(@NotNull Long processoId, LocalDate data, @NotBlank String descricao) {
	}

	record Repasse(String via) {
	}

	private final AtendimentoService servico;

	AtendimentoController(AtendimentoService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/atendimentos")
	List<AtendimentoDto> listar(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(required = false) Long clienteId) {
		return servico.listar(u, clienteId);
	}

	@PostMapping("/api/atendimentos")
	@ResponseStatus(HttpStatus.CREATED)
	AtendimentoDto registrar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovoAtendimento d) {
		return servico.registrarAvulso(u, d.clienteId(), d.processoId(), d.canal(), d.momento(), d.resumo(), d.atendenteId());
	}

	@GetMapping("/api/andamentos/pendentes")
	List<AndamentoDto> pendentes(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.pendentesDeRepasse(u);
	}

	@PostMapping("/api/andamentos")
	@ResponseStatus(HttpStatus.CREATED)
	AndamentoDto novoAndamento(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovoAndamento d) {
		return servico.registrarAndamento(u, d.processoId(), d.data(), d.descricao());
	}

	@PostMapping("/api/andamentos/{id}/repassar")
	AndamentoDto repassar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestBody(required = false) Repasse r) {
		return servico.marcarRepassado(u, id, r == null ? null : r.via());
	}

	@ExceptionHandler({AtendimentoNaoEncontradoException.class, CadastroNaoEncontradoException.class})
	ProblemDetail naoEncontrado(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	ProblemDetail invalido(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	@ExceptionHandler(SecurityException.class)
	ProblemDetail negado(SecurityException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}
}
