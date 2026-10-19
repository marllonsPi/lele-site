package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AgendaController {

	record NovoCompromisso(@NotNull TipoAgendamento tipo, Long processoId, Long clienteId,
			@Size(max = 300) String descricao, @NotNull Long responsavelId, boolean urgente,
			@NotNull LocalDateTime inicio, Integer duracaoMin, @NotNull Modalidade modalidade,
			@Size(max = 300) String localOuLink) {
	}

	private final AgendaService agenda;
	private final CompromissoService compromissos;

	AgendaController(AgendaService agenda, CompromissoService compromissos) {
		this.agenda = agenda;
		this.compromissos = compromissos;
	}

	@GetMapping("/api/agenda")
	List<AgendaItemDto> agenda(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(required = false) Long usuarioId) {
		return agenda.agenda(u, usuarioId);
	}

	@PostMapping("/api/compromissos")
	@ResponseStatus(HttpStatus.CREATED)
	CompromissoService.Marcado marcar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovoCompromisso d) {
		return compromissos.marcar(u, d.tipo(), d.processoId(), d.clienteId(), d.descricao(), d.responsavelId(),
				d.urgente(), d.inicio(), d.duracaoMin(), d.modalidade(), d.localOuLink());
	}
}
