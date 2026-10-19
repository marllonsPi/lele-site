package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ConclusaoController {

	record Motivo(@NotBlank @Size(max = 2000) String motivo) {
	}

	private final ConclusaoService servico;

	ConclusaoController(ConclusaoService servico) {
		this.servico = servico;
	}

	record Resumo(@Size(max = 5000) String resumo) {
	}

	@PostMapping("/api/agendamentos/{id}/finalizar")
	AgendaItemDto finalizar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@Valid @RequestBody(required = false) Resumo r) {
		return servico.finalizar(u, id, r == null ? null : r.resumo());
	}

	@PostMapping("/api/agendamentos/{id}/aprovar-supervisor")
	AgendaItemDto aprovarSupervisor(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.aprovarSupervisor(u, id);
	}

	@PostMapping("/api/agendamentos/{id}/devolver-supervisor")
	AgendaItemDto devolverSupervisor(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Motivo m) {
		return servico.devolverSupervisor(u, id, m.motivo());
	}

	@PostMapping("/api/agendamentos/{id}/conferir-socia")
	AgendaItemDto conferirSocia(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.conferirSocia(u, id);
	}

	@PostMapping("/api/agendamentos/{id}/devolver")
	AgendaItemDto devolver(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Motivo m) {
		return servico.devolverSocia(u, id, m.motivo());
	}

	@GetMapping("/api/agendamentos/{id}/conferencias")
	List<ConferenciaDto> historico(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.historico(u, id);
	}

	@GetMapping("/api/conferencias/pendentes")
	List<AgendaItemDto> pendentes(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.pendentesDeConferencia(u);
	}
}
