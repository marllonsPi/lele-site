package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.DadosItem;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.ItemDto;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.ResultadoImportacao;
import br.com.crispimcampos.gestao.honorarios.TabelaHonorariosDtos.VersaoDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
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
import org.springframework.web.multipart.MultipartFile;

/** Tabelas, modelo do contrato, propostas e relatorios de honorarios: exclusivo das socias. */
@RestController
@PreAuthorize("hasRole('SOCIA')")
class HonorariosController {

	record Urh(@NotNull BigDecimal valor) {
	}

	record Politica(BigDecimal fator, BigDecimal descontoPadrao, BigDecimal descontoTrabalhista, BigDecimal descontoPacote) {
	}

	record Modelo(@NotBlank String texto) {
	}

	private final TabelaHonorariosService tabelas;
	private final MinutaContratoService minuta;
	private final RelatorioHonorariosService relatorios;
	private final PropostaService propostas;

	HonorariosController(TabelaHonorariosService tabelas, MinutaContratoService minuta, RelatorioHonorariosService relatorios, PropostaService propostas) {
		this.tabelas = tabelas;
		this.minuta = minuta;
		this.relatorios = relatorios;
		this.propostas = propostas;
	}

	@GetMapping("/api/honorarios/tabelas/versoes")
	List<VersaoDto> versoes(@AuthenticationPrincipal UsuarioLogado u) {
		return tabelas.versoes(u);
	}

	@GetMapping("/api/honorarios/tabelas/versoes/{id}/itens")
	List<ItemDto> itens(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestParam(defaultValue = "false") boolean excluidos) {
		return tabelas.itens(u, id, excluidos);
	}

	@PostMapping("/api/honorarios/tabelas/versoes/{id}/itens")
	@ResponseStatus(HttpStatus.CREATED)
	ItemDto criar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestBody DadosItem d) {
		return tabelas.criarItem(u, id, d);
	}

	@PutMapping("/api/honorarios/tabelas/itens/{id}")
	ItemDto atualizar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestBody DadosItem d) {
		return tabelas.atualizarItem(u, id, d);
	}

	@DeleteMapping("/api/honorarios/tabelas/itens/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void excluir(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		tabelas.excluirItem(u, id);
	}

	@PostMapping("/api/honorarios/tabelas/itens/{id}/restaurar")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void restaurar(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id) {
		tabelas.restaurarItem(u, id);
	}

	@PutMapping("/api/honorarios/tabelas/oab/{id}/urh")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void urh(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @Valid @RequestBody Urh d) {
		tabelas.atualizarUrh(u, id, d.valor());
	}

	@PutMapping("/api/honorarios/tabelas/escritorio/{id}/politica")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void politica(@AuthenticationPrincipal UsuarioLogado u, @PathVariable Long id, @RequestBody Politica p) {
		tabelas.atualizarPolitica(u, id, p.fator(), p.descontoPadrao(), p.descontoTrabalhista(), p.descontoPacote());
	}

	@PostMapping("/api/honorarios/tabelas/importar")
	@ResponseStatus(HttpStatus.CREATED)
	ResultadoImportacao importar(@AuthenticationPrincipal UsuarioLogado u, @RequestParam("arquivo") MultipartFile arquivo, @RequestParam(defaultValue = "DF") Seccional seccional,
			@RequestParam(defaultValue = "true") boolean substituirEscritorio) throws IOException {
		return tabelas.importar(u, arquivo.getOriginalFilename(), arquivo.getBytes(), seccional, substituirEscritorio);
	}

	@GetMapping("/api/honorarios/modelo-contrato")
	Map<String, String> modelo(@AuthenticationPrincipal UsuarioLogado u) {
		return Map.of("texto", minuta.modelo(u));
	}

	@PutMapping("/api/honorarios/modelo-contrato")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void salvarModelo(@AuthenticationPrincipal UsuarioLogado u, @Valid @RequestBody Modelo m) {
		minuta.salvarModelo(u, m.texto());
	}

	@GetMapping("/api/honorarios/relatorios")
	RelatorioHonorariosService.Relatorio relatorio(@AuthenticationPrincipal UsuarioLogado u, @RequestParam(required = false) LocalDate de, @RequestParam(required = false) LocalDate ate) {
		return relatorios.gerar(u, de, ate);
	}

	@GetMapping("/api/honorarios/propostas")
	List<PropostaService.Resumo> propostas(@AuthenticationPrincipal UsuarioLogado u) {
		return propostas.listar(u);
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
