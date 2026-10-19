package br.com.crispimcampos.gestao.modelos;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class ModeloController {

	private final ModeloService servico;

	ModeloController(ModeloService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/modelos")
	List<ModeloDto> listar(@RequestParam(required = false) String tipo, @RequestParam(required = false) String area) {
		return servico.listar(tipo, area);
	}

	@GetMapping("/api/modelos/areas")
	List<String> areas() {
		return servico.areas();
	}

	@PostMapping("/api/modelos")
	@ResponseStatus(HttpStatus.CREATED)
	ModeloDto criar(@AuthenticationPrincipal UsuarioLogado u, @RequestParam String tipo, @RequestParam String area,
			@RequestParam String titulo, @RequestParam("arquivo") MultipartFile arquivo) throws IOException {
		return servico.criar(u, tipo, area, titulo, arquivo.getOriginalFilename(), arquivo.getBytes());
	}

	@DeleteMapping("/api/modelos/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void desativar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		servico.desativar(u, id);
	}

	@GetMapping("/api/modelos/{id}/download")
	ResponseEntity<Resource> baixar(@PathVariable Long id) {
		ModeloService.Download d = servico.baixar(id);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.nome(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
				.body(d.arquivo());
	}

	@ExceptionHandler(ModeloNaoEncontradoException.class)
	ProblemDetail naoEncontrado(ModeloNaoEncontradoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
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
