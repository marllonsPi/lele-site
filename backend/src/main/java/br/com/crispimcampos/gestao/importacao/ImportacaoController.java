package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Acao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Decisao;
import br.com.crispimcampos.gestao.importacao.ItemPrevia.Tipo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.multipart.MultipartFile;

/** Importacao (previa, decisoes, confirmacao): exclusivo do acesso mestre (suporte do sistema). */
@RestController
@PreAuthorize("hasRole('MESTRE')")
class ImportacaoController {

	record Ajuste(Decisao decisao, Map<String, Object> ajustes) {
	}

	record Lote(@NotNull Acao acao, Tipo tipo, @NotNull Decisao decisao) {
	}

	private final ImportacaoService servico;

	ImportacaoController(ImportacaoService servico) {
		this.servico = servico;
	}

	@PostMapping("/api/importacoes/planilha")
	@ResponseStatus(HttpStatus.CREATED)
	ImportacaoDto planilha(@AuthenticationPrincipal UsuarioLogado u, @RequestParam("arquivo") MultipartFile arquivo,
			@RequestParam(value = "responsavelId", required = false) Long responsavelId) throws IOException {
		return servico.gerarPreviaPlanilha(u, arquivo.getOriginalFilename(), arquivo.getBytes(), responsavelId);
	}

	@PostMapping("/api/importacoes/drive")
	@ResponseStatus(HttpStatus.CREATED)
	ImportacaoDto drive(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(value = "responsavelId", required = false) Long responsavelId) {
		return servico.gerarPreviaDrive(u, responsavelId);
	}

	@PostMapping("/api/importacoes/planilha-drive")
	@ResponseStatus(HttpStatus.CREATED)
	ImportacaoDto planilhaDoDrive(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(value = "responsavelId", required = false) Long responsavelId) {
		return servico.gerarPreviaPlanilhaDoDrive(u, responsavelId);
	}

	@GetMapping("/api/importacoes/drive/status")
	Map<String, Object> statusDrive(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.statusDrive(u);
	}

	@GetMapping("/api/importacoes")
	List<ImportacaoDto> listar(@AuthenticationPrincipal UsuarioLogado u) {
		return servico.listar(u);
	}

	@GetMapping("/api/importacoes/{id}")
	ImportacaoDto detalhe(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.detalhe(u, id);
	}

	@PutMapping("/api/importacoes/{id}/itens/{itemId}")
	ImportacaoDto decidir(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody Ajuste a) {
		return servico.decidir(u, id, itemId, a.decisao(), a.ajustes());
	}

	@PostMapping("/api/importacoes/{id}/decidir-lote")
	ImportacaoDto lote(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Lote l) {
		return servico.decidirEmLote(u, id, l.acao(), l.tipo(), l.decisao());
	}

	@PostMapping("/api/importacoes/{id}/confirmar")
	ImportacaoDto confirmar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.confirmar(u, id);
	}

	@PostMapping("/api/importacoes/{id}/cancelar")
	ImportacaoDto cancelar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.cancelar(u, id);
	}

	@ExceptionHandler(PendenciaDeImportacaoException.class)
	ProblemDetail pendencias(PendenciaDeImportacaoException e) {
		ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
		p.setProperty("pendencias", e.pendencias());
		return p;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalido(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	@ExceptionHandler(SecurityException.class)
	ProblemDetail negado(SecurityException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}
}
