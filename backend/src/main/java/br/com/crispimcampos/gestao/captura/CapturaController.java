package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
class CapturaController {

	record Classificacao(@NotNull TipoAgendamento tipo, Long processoId, @NotNull Long responsavelId, boolean urgente,
			String descricao, @NotNull LocalDate inicioPrazo, @NotNull LocalDate prazoFatal) {
	}

	record Descarte(@NotBlank String motivo) {
	}

	record Vinculo(@NotNull Long processoId) {
	}

	record Execucao(LocalDate de, LocalDate ate) {
	}

	record ItemManual(@NotNull Fonte fonte, @NotBlank String idExterno, String numeroProcesso, String tribunal,
			@NotNull LocalDate dataPublicacao, @NotBlank String conteudo) {
	}

	record Importacao(@NotNull Long titularId, @NotEmpty List<@Valid ItemManual> itens) {
	}

	record NovoFiltro(@NotNull Long usuarioId, @NotNull Fonte fonte, @NotBlank String tribunalPrefixo, String excecoes) {
	}

	private final PublicacaoService publicacoes;
	private final CapturaService captura;
	private final FiltroService filtros;

	CapturaController(PublicacaoService publicacoes, CapturaService captura, FiltroService filtros) {
		this.publicacoes = publicacoes;
		this.captura = captura;
		this.filtros = filtros;
	}

	@GetMapping("/api/publicacoes")
	List<PublicacaoDto> listar(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(required = false) EstadoPublicacao estado) {
		return publicacoes.listar(u, estado);
	}

	@GetMapping("/api/publicacoes/{id}")
	PublicacaoDto buscar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return publicacoes.buscar(u, id);
	}

	@PostMapping("/api/publicacoes/{id}/classificar")
	PublicacaoDto classificar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Classificacao c) {
		return publicacoes.classificar(u, id, c.tipo(), c.processoId(), c.responsavelId(), c.urgente(), c.descricao(),
				c.inicioPrazo(), c.prazoFatal());
	}

	@PostMapping("/api/publicacoes/{id}/descartar")
	PublicacaoDto descartar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Descarte d) {
		return publicacoes.descartar(u, id, d.motivo());
	}

	@PutMapping("/api/publicacoes/{id}/processo")
	PublicacaoDto vincular(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Vinculo v) {
		return publicacoes.vincularProcesso(u, id, v.processoId());
	}

	@PostMapping("/api/captura/executar")
	@PreAuthorize("hasRole('SOCIA')")
	List<CapturaService.Execucao> executar(@AuthenticationPrincipal UsuarioLogado u, @RequestBody(required = false) Execucao e) {
		LocalDate ate = e != null && e.ate() != null ? e.ate() : LocalDate.now();
		LocalDate de = e != null && e.de() != null ? e.de() : ate.minusDays(3);
		return captura.executar(u.id(), de, ate);
	}

	@GetMapping("/api/captura/execucoes")
	@PreAuthorize("hasRole('SOCIA')")
	List<CapturaService.Execucao> execucoes() {
		return captura.execucoes();
	}

	@GetMapping("/api/captura/ignorados")
	@PreAuthorize("hasRole('SOCIA')")
	List<Map<String, Object>> ignorados() {
		return captura.ignorados();
	}

	@PostMapping("/api/captura/importar")
	@PreAuthorize("hasRole('SOCIA')")
	PipelineCaptura.Resultado importar(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody Importacao i) {
		List<DadosBrutos> itens = i.itens().stream().map(m -> new DadosBrutos(m.fonte(), m.idExterno(), m.numeroProcesso(),
				m.tribunal(), m.dataPublicacao(), m.conteudo(), Hashes.sha256(m.conteudo()))).toList();
		return captura.importar(u.id(), i.titularId(), itens);
	}

	@GetMapping("/api/captura/filtros")
	@PreAuthorize("hasRole('SOCIA')")
	List<FiltroService.FiltroDto> filtros() {
		return filtros.listar();
	}

	@PostMapping("/api/captura/filtros")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('SOCIA')")
	FiltroService.FiltroDto criarFiltro(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovoFiltro f) {
		return filtros.criar(u.id(), f.usuarioId(), f.fonte(), f.tribunalPrefixo(), f.excecoes());
	}

	@DeleteMapping("/api/captura/filtros/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasRole('SOCIA')")
	void desativarFiltro(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		filtros.desativar(u.id(), id);
	}

	@ExceptionHandler(CapturaNaoEncontradaException.class)
	ProblemDetail naoEncontrada(CapturaNaoEncontradaException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	ProblemDetail invalido(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	@ExceptionHandler(SecurityException.class)
	ProblemDetail negado(SecurityException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}
}
