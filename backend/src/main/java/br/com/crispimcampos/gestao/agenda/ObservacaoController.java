package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ObservacaoController {

	record NovaObservacao(@NotBlank @Size(max = ObservacaoService.TEXTO_MAXIMO) String texto) {
	}

	private final ObservacaoService servico;

	ObservacaoController(ObservacaoService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/agendamentos/{id}/observacoes")
	List<ObservacaoDto> listar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.listar(u, id);
	}

	@PostMapping("/api/agendamentos/{id}/observacoes")
	@ResponseStatus(HttpStatus.CREATED)
	ObservacaoDto adicionar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@Valid @RequestBody NovaObservacao d) {
		return servico.adicionar(u, id, d.texto());
	}
}
