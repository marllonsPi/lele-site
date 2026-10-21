package br.com.crispimcampos.gestao.documentos;

import br.com.crispimcampos.gestao.cadastros.CadastroNaoEncontradoException;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Sugestoes e associacao de arquivos do Drive ao cliente (somente socias; o servico confere). */
@RestController
class DocumentoDriveController {

	record Associar(List<DocumentoDriveService.Escolha> itens) {
	}

	private final DocumentoDriveService servico;
	private final DriveMigracaoService migracao;

	DocumentoDriveController(DocumentoDriveService servico, DriveMigracaoService migracao) {
		this.servico = servico;
		this.migracao = migracao;
	}

	@GetMapping("/api/drive/locais")
	@org.springframework.security.access.prepost.PreAuthorize("hasRole('SOCIA')")
	Map<String, Integer> locais() {
		return Map.of("pendentes", migracao.pendentes());
	}

	@PostMapping("/api/drive/mover-locais")
	@org.springframework.security.access.prepost.PreAuthorize("hasRole('SOCIA')")
	Map<String, Integer> moverLocais(@AuthenticationPrincipal UsuarioLogado u) {
		return migracao.moverLote(u.id());
	}

	@GetMapping("/api/clientes/{id}/drive/sugestoes")
	DocumentoDriveService.Sugestoes sugestoes(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.sugerir(u, id);
	}

	@PostMapping("/api/clientes/{id}/drive/associar")
	Map<String, Integer> associar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestBody Associar c) {
		return Map.of("associados", servico.associar(u, id, c.itens() == null ? List.of() : c.itens()));
	}

	@ExceptionHandler({IllegalArgumentException.class, CadastroNaoEncontradoException.class})
	ProblemDetail invalido(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(e instanceof CadastroNaoEncontradoException ? HttpStatus.NOT_FOUND : HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail inativo(IllegalStateException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(SecurityException.class)
	ProblemDetail negado(SecurityException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}
}
