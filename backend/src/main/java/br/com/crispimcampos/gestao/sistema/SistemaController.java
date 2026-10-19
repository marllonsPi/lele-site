package br.com.crispimcampos.gestao.sistema;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Configuracao do sistema: exclusiva do acesso mestre. */
@RestController
@PreAuthorize("hasRole('MESTRE')")
class SistemaController {

	record Configuracao(List<ConfiguracaoService.Campo> campos, Map<String, String> valores) {
	}

	private final ConfiguracaoService configuracao;
	private final ProntidaoService prontidao;

	SistemaController(ConfiguracaoService configuracao, ProntidaoService prontidao) {
		this.configuracao = configuracao;
		this.prontidao = prontidao;
	}

	@GetMapping("/api/sistema/configuracao")
	Configuracao ler() {
		return new Configuracao(ConfiguracaoService.CATALOGO, configuracao.todas());
	}

	@PutMapping("/api/sistema/configuracao")
	Configuracao salvar(@AuthenticationPrincipal UsuarioLogado ator, @RequestBody Map<String, String> valores) {
		return new Configuracao(ConfiguracaoService.CATALOGO, configuracao.salvar(ator.id(), valores));
	}

	@GetMapping("/api/sistema/prontidao")
	List<ProntidaoService.Item> prontidao() {
		return prontidao.diagnosticar();
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalido(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}
}
