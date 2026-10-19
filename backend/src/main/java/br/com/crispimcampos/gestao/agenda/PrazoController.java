package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quem pode o que e decidido no servico (depende do perfil e da visibilidade, nao so da rota). */
@RestController
class PrazoController {

	record NovoPrazo(@NotNull Long processoId, @NotNull TipoAgendamento tipo, @Size(max = 300) String descricao,
			@NotNull Long responsavelId, boolean urgente, @NotNull LocalDate inicioPrazo, @NotNull LocalDate prazoFatal) {
	}

	record CorrecaoPrazo(@Size(max = 300) String descricao, @NotNull Long responsavelId, boolean urgente,
			@NotNull LocalDate inicioPrazo, @NotNull LocalDate prazoFatal) {
	}

	private final PrazoService servico;

	PrazoController(PrazoService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/prazos")
	List<PrazoDto> listar(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.listar(u);
	}

	@GetMapping("/api/prazos/{id}")
	PrazoDto buscar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.buscar(u, id);
	}

	@PostMapping("/api/prazos")
	@ResponseStatus(HttpStatus.CREATED)
	PrazoDto lancar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovoPrazo d) {
		return servico.lancar(u, d.processoId(), d.tipo(), d.descricao(), d.responsavelId(), d.urgente(),
				d.inicioPrazo(), d.prazoFatal());
	}

	@PutMapping("/api/prazos/{id}")
	PrazoDto corrigir(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@Valid @RequestBody CorrecaoPrazo d) {
		return servico.corrigir(u, id, d.descricao(), d.responsavelId(), d.urgente(), d.inicioPrazo(), d.prazoFatal());
	}

	@PostMapping("/api/prazos/{id}/conferir")
	PrazoDto conferir(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.conferir(u, id);
	}
}
