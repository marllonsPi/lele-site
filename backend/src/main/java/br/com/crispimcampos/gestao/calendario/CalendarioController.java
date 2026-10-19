package br.com.crispimcampos.gestao.calendario;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Calendario legivel por todos; editavel so pelas socias. */
@RestController
class CalendarioController {

	record DadosFeriado(@NotNull EscopoFeriado escopo, @Size(max = 80) String referencia, @NotNull TipoFeriado tipo,
			@NotNull LocalDate dataInicial, LocalDate dataFinal, @NotBlank @Size(max = 200) String descricao) {
	}

	private final CalendarioService servico;

	CalendarioController(CalendarioService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/calendario")
	List<FeriadoDto> listar() {
		return servico.listar();
	}

	@PostMapping("/api/calendario")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('SOCIA')")
	FeriadoDto criar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody DadosFeriado d) {
		return servico.criar(u.id(), d.escopo(), d.referencia(), d.tipo(), d.dataInicial(), d.dataFinal(), d.descricao());
	}

	@PutMapping("/api/calendario/{id}")
	@PreAuthorize("hasRole('SOCIA')")
	FeriadoDto atualizar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@Valid @RequestBody DadosFeriado d) {
		return servico.atualizar(u.id(), id, d.escopo(), d.referencia(), d.tipo(), d.dataInicial(), d.dataFinal(),
				d.descricao());
	}

	@DeleteMapping("/api/calendario/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('SOCIA')")
	void desativar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		servico.desativar(u.id(), id);
	}

	@ExceptionHandler(CalendarioException.class)
	ProblemDetail erro(CalendarioException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}
}
