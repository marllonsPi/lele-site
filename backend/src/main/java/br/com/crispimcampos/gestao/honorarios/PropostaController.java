package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.honorarios.PropostaDtos.Fechamento;
import br.com.crispimcampos.gestao.honorarios.PropostaDtos.PropostaDto;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.ServicoCatalogo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quadro de sugestao e fechamento no atendimento: socias e advogado que conduz o atendimento. Assistente e estagiario ficam de fora
 * (negado antes do servico); o servico ainda confere que o advogado e o atendente.
 */
@RestController
@PreAuthorize("hasAnyRole('SOCIA', 'ADVOGADO')")
class PropostaController {

	record Servicos(@NotNull Seccional seccional, @NotEmpty List<String> servicos) {
	}

	record Assinatura(LocalDate dataAssinatura) {
	}

	private final PropostaService propostas;
	private final TabelaHonorariosService tabelas;
	private final MinutaContratoService minuta;

	PropostaController(PropostaService propostas, TabelaHonorariosService tabelas, MinutaContratoService minuta) {
		this.propostas = propostas;
		this.tabelas = tabelas;
		this.minuta = minuta;
	}

	@GetMapping("/api/honorarios/catalogo")
	List<ServicoCatalogo> catalogo(@AuthenticationPrincipal UsuarioLogado u) {
		return tabelas.catalogo(u);
	}

	@GetMapping("/api/atendimentos/{agendamentoId}/proposta")
	ResponseEntity<PropostaDto> doAtendimento(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long agendamentoId) {
		PropostaDto p = propostas.doAtendimento(u, agendamentoId);
		return p == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(p);
	}

	@PostMapping("/api/atendimentos/{agendamentoId}/proposta/sugestao")
	PropostaDto sugerir(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long agendamentoId, @Valid @RequestBody Servicos s) {
		return propostas.sugerirNoAtendimento(u, agendamentoId, s.seccional(), s.servicos());
	}

	@PostMapping("/api/clientes/{clienteId}/proposta/sugestao")
	PropostaDto sugerirParaCliente(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long clienteId, @Valid @RequestBody Servicos s) {
		return propostas.sugerirParaCliente(u, clienteId, s.seccional(), s.servicos());
	}

	@PutMapping("/api/propostas/{id}/servicos")
	PropostaDto servicos(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Servicos s) {
		return propostas.recalcular(u, id, s.seccional(), s.servicos());
	}

	@GetMapping("/api/propostas/{id}")
	PropostaDto buscar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return propostas.buscar(u, id);
	}

	@PutMapping("/api/propostas/{id}/fechamento")
	PropostaDto fechar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestBody Fechamento f) {
		return propostas.fechar(u, id, f);
	}

	@PostMapping("/api/propostas/{id}/nao-fechada")
	PropostaDto naoFechada(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return propostas.naoFechada(u, id);
	}

	@PostMapping("/api/propostas/{id}/assinar")
	PropostaDto assinar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestBody(required = false) Assinatura a) {
		return propostas.assinar(u, id, a == null ? null : a.dataAssinatura());
	}

	/** Sempre como anexo binario. A minuta tambem fica na pasta do cliente (restrita as socias). */
	@PostMapping("/api/propostas/{id}/minuta")
	ResponseEntity<byte[]> gerarMinuta(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		MinutaContratoService.Minuta m = minuta.gerar(u, id);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(m.nomeArquivo(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
				.header("X-Content-Type-Options", "nosniff").body(m.conteudo());
	}

	@ExceptionHandler(PropostaNaoEncontradaException.class)
	ProblemDetail naoEncontrada(PropostaNaoEncontradaException e) {
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
