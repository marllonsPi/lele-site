package br.com.crispimcampos.gestao.documentos;

import br.com.crispimcampos.gestao.cadastros.CadastroNaoEncontradoException;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class DocumentoClienteController {

	private final DocumentoClienteService servico;

	DocumentoClienteController(DocumentoClienteService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/clientes/{id}/documentos")
	List<DocumentoClienteDto> listar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.listar(u, id);
	}

	@PostMapping("/api/clientes/{id}/documentos")
	@ResponseStatus(HttpStatus.CREATED)
	DocumentoClienteDto enviar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo,
			@RequestParam(value = "tipo", required = false) TipoDocumento tipo) throws IOException {
		TipoDocumento t = tipo != null ? tipo : TipoDocumento.pelNome(arquivo.getOriginalFilename());
		return servico.enviar(u, id, t, arquivo.getOriginalFilename(), arquivo.getBytes());
	}

	/** Sempre como anexo binario, nunca renderizado no navegador. */
	@GetMapping("/api/documentos/{id}/download")
	ResponseEntity<Resource> baixar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		DocumentoClienteService.Download d = servico.baixar(u, id);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.nome(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff").body(d.arquivo());
	}

	@ExceptionHandler({IllegalArgumentException.class, CadastroNaoEncontradoException.class})
	ProblemDetail naoEncontrado(RuntimeException e) {
		boolean nao = e instanceof CadastroNaoEncontradoException || String.valueOf(e.getMessage()).contains("nao encontrado");
		return ProblemDetail.forStatusAndDetail(nao ? HttpStatus.NOT_FOUND : HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	@ExceptionHandler(SecurityException.class)
	ProblemDetail negado(SecurityException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}
}
