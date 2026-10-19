package br.com.crispimcampos.gestao.relatorios;

import br.com.crispimcampos.gestao.cadastros.CadastroNaoEncontradoException;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RelatorioController {

	record NovoRelatorio(@NotNull Long clienteId, List<Long> processoIds, LocalDate de, LocalDate ate, boolean desdeUltimoRelatorio,
			boolean incluirFinanceiro) {
	}

	record Envio(@NotNull String canal) {
	}

	private final RelatorioClienteService cliente;
	private final RelatoriosInternosService internos;

	RelatorioController(RelatorioClienteService cliente, RelatoriosInternosService internos) {
		this.cliente = cliente;
		this.internos = internos;
	}

	// ---- 14.2 relatorio ao cliente

	@PostMapping("/api/relatorios/clientes")
	@ResponseStatus(HttpStatus.CREATED)
	RelatorioClienteDto criar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovoRelatorio d) {
		return cliente.criarRascunho(u, d.clienteId(), d.processoIds(), d.de(), d.ate(), d.desdeUltimoRelatorio(), d.incluirFinanceiro());
	}

	@GetMapping("/api/relatorios/clientes/{id}")
	RelatorioClienteDto buscar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return cliente.buscar(u, id);
	}

	@GetMapping("/api/clientes/{clienteId}/relatorios")
	List<RelatorioClienteDto> doCliente(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long clienteId) {
		return cliente.doCliente(u, clienteId);
	}

	@PutMapping("/api/relatorios/clientes/{id}")
	RelatorioClienteDto editar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestBody ConteudoCliente c) {
		return cliente.editar(u, id, c);
	}

	@PostMapping("/api/relatorios/clientes/{id}/aprovar")
	RelatorioClienteDto aprovar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return cliente.aprovar(u, id);
	}

	@GetMapping("/api/relatorios/clientes/{id}/pdf")
	ResponseEntity<byte[]> pdfCliente(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return pdf(cliente.gerarPdf(u, id), "relatorio-" + id + ".pdf");
	}

	@PostMapping("/api/relatorios/clientes/{id}/enviar")
	RelatorioClienteDto enviar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Envio e) {
		return cliente.enviar(u, id, e.canal());
	}

	// ---- 14.1 relatorios internos

	@GetMapping("/api/relatorios/internos/{tipo}")
	RelatorioDados interno(@AuthenticationPrincipal UsuarioLogado u, @PathVariable RelatoriosInternosService.Tipo tipo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
			@RequestParam(required = false) Long usuarioId, @RequestParam(required = false) Long clienteId) {
		return internos.gerar(u, tipo, de, ate, usuarioId, clienteId);
	}

	@GetMapping("/api/relatorios/internos/{tipo}/pdf")
	ResponseEntity<byte[]> internoPdf(@AuthenticationPrincipal UsuarioLogado u, @PathVariable RelatoriosInternosService.Tipo tipo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
			@RequestParam(required = false) Long usuarioId, @RequestParam(required = false) Long clienteId) {
		return pdf(internos.pdf(u, tipo, de, ate, usuarioId, clienteId), "relatorio-" + tipo.name().toLowerCase() + ".pdf");
	}

	@PostMapping("/api/relatorios/internos/{tipo}/enviar")
	Map<String, Integer> enviarAsSocias(@AuthenticationPrincipal UsuarioLogado u, @PathVariable RelatoriosInternosService.Tipo tipo,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
			@RequestParam(required = false) Long usuarioId, @RequestParam(required = false) Long clienteId) {
		return Map.of("enviados", internos.enviarAsSocias(u, tipo, de, ate, usuarioId, clienteId));
	}

	private static ResponseEntity<byte[]> pdf(byte[] arquivo, String nome) {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString()).body(arquivo);
	}

	@ExceptionHandler({RelatorioNaoEncontradoException.class, CadastroNaoEncontradoException.class})
	ProblemDetail naoEncontrado(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, UnsupportedOperationException.class})
	ProblemDetail invalido(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	@ExceptionHandler(SecurityException.class)
	ProblemDetail negado(SecurityException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}
}
