package br.com.crispimcampos.gestao.alertas;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AlertaController {

	private final AlertaService servico;

	AlertaController(AlertaService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/alertas")
	List<NotificacaoDto> listar(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.listar(u);
	}

	@PostMapping("/api/alertas/executar")
	@PreAuthorize("hasRole('SOCIA')")
	AlertaService.Resultado executar(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.executar(u.id());
	}
}
