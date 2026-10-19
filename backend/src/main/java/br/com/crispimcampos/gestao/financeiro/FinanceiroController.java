package br.com.crispimcampos.gestao.financeiro;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tudo aqui e exclusivo das socias (negado antes de chegar ao servico). */
@RestController
@PreAuthorize("hasRole('SOCIA')")
class FinanceiroController {

	record NovoContrato(@NotNull Long clienteId, Long processoId, @NotBlank String descricao, @NotNull BigDecimal valorTotal,
			@Min(1) @Max(120) int parcelas, @NotNull LocalDate primeiroVencimento) {
	}

	record Pagamento(LocalDate data, @NotNull BigDecimal valor) {
	}

	record NovaDespesa(@NotNull Long clienteId, Long processoId, @NotBlank String tipo, @NotBlank String descricao,
			@NotNull BigDecimal valor, LocalDate data, boolean reembolsavel) {
	}

	record NovoVariavel(@NotNull Long clienteId, Long processoId, @NotBlank String tipo, @NotBlank String descricao,
			@NotNull BigDecimal valorPrevisto) {
	}

	private final FinanceiroService servico;

	FinanceiroController(FinanceiroService servico) {
		this.servico = servico;
	}

	@PostMapping("/api/financeiro/contratos")
	@ResponseStatus(HttpStatus.CREATED)
	Map<String, Long> contrato(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovoContrato d) {
		return Map.of("id", servico.criarContrato(u, d.clienteId(), d.processoId(), d.descricao(), d.valorTotal(), d.parcelas(), d.primeiroVencimento()));
	}

	@GetMapping("/api/financeiro/contratos")
	List<Map<String, Object>> contratos(@AuthenticationPrincipal UsuarioLogado u, @RequestParam Long clienteId) {
		return servico.contratos(u, clienteId);
	}

	@GetMapping("/api/financeiro/contratos/{id}/parcelas")
	List<Map<String, Object>> parcelas(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.parcelas(u, id);
	}

	@PostMapping("/api/financeiro/parcelas/{id}/pagar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void pagar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Pagamento p) {
		servico.pagarParcela(u, id, p.data(), p.valor());
	}

	@PostMapping("/api/financeiro/despesas")
	@ResponseStatus(HttpStatus.CREATED)
	Map<String, Long> despesa(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovaDespesa d) {
		return Map.of("id", servico.registrarDespesa(u, d.clienteId(), d.processoId(), d.tipo(), d.descricao(), d.valor(), d.data(), d.reembolsavel()));
	}

	@PostMapping("/api/financeiro/honorarios")
	@ResponseStatus(HttpStatus.CREATED)
	Map<String, Long> variavel(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody NovoVariavel d) {
		return Map.of("id", servico.registrarHonorarioVariavel(u, d.clienteId(), d.processoId(), d.tipo(), d.descricao(), d.valorPrevisto()));
	}

	@PostMapping("/api/financeiro/honorarios/{id}/receber")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void receber(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Pagamento p) {
		servico.receberHonorarioVariavel(u, id, p.data(), p.valor());
	}

	@GetMapping("/api/financeiro/clientes/{id}/resumo")
	ResumoFinanceiro resumoCliente(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.resumoDoCliente(u, id);
	}

	@GetMapping("/api/financeiro/processos/{id}/resumo")
	ResumoFinanceiro resumoProcesso(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		return servico.resumoDoProcesso(u, id);
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
