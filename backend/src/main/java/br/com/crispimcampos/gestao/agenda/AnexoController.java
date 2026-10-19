package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class AnexoController {

	private final AnexoService servico;

	AnexoController(AnexoService servico) {
		this.servico = servico;
	}

	@GetMapping("/api/agendamentos/{id}/anexos")
	List<AnexoDto> doAgendamento(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.listarDoAgendamento(u, id);
	}

	@PostMapping("/api/agendamentos/{id}/anexos")
	@ResponseStatus(HttpStatus.CREATED)
	AnexoDto anexarAoAgendamento(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
		return servico.anexarAoAgendamento(u, id, arquivo.getOriginalFilename(), arquivo.getBytes());
	}

	@GetMapping("/api/processos/{id}/anexos")
	List<AnexoDto> doProcesso(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.listarDoProcesso(u, id);
	}

	@PostMapping("/api/processos/{id}/anexos")
	@ResponseStatus(HttpStatus.CREATED)
	AnexoDto anexarAoProcesso(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id,
			@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
		return servico.anexarAoProcesso(u, id, arquivo.getOriginalFilename(), arquivo.getBytes());
	}

	/** Sempre como anexo binario, nunca renderizado no navegador. */
	@GetMapping("/api/anexos/{id}/download")
	ResponseEntity<Resource> baixar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		AnexoService.Download d = servico.baixar(u, id);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(d.nome(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
				.body(d.arquivo());
	}
}
