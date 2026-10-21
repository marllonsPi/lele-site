package br.com.crispimcampos.gestao.comentarios;

import br.com.crispimcampos.gestao.comentarios.ComentarioService.ComentarioInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Parte publica (sem login) dos comentarios do site: listar aprovados, enviar um novo e a pagina de decisao do link do
 * e-mail. Abrir o link so MOSTRA a pagina; quem decide e o botao (POST), para que leitores automaticos de e-mail nao aprovem.
 */
@RestController
class ComentarioPublicoController {

	record Novo(@Size(max = 200) String nome, @Size(max = 400) String email, @Size(max = 2000) String mensagem, String website) {
	}

	private final ComentarioService servico;
	private final LimiteDeEnvio limite;

	ComentarioPublicoController(ComentarioService servico, LimiteDeEnvio limite) {
		this.servico = servico;
		this.limite = limite;
	}

	@GetMapping("/api/public/comentarios")
	ResponseEntity<List<ComentarioService.Publico>> aprovados() {
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()).body(servico.aprovados());
	}

	@PostMapping("/api/public/comentarios")
	ResponseEntity<Void> enviar(@RequestBody Novo c, HttpServletRequest req) {
		// campo isca: robos preenchem, pessoas nao veem; responde igual para nao dar pista
		if (c.website() != null && !c.website().isBlank()) {
			return ResponseEntity.accepted().build();
		}
		if (!limite.permitir(origem(req))) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
		}
		servico.receber(c.nome(), c.email(), c.mensagem());
		return ResponseEntity.accepted().build();
	}

	@GetMapping(value = "/api/public/comentarios/moderar", produces = MediaType.TEXT_HTML_VALUE)
	ResponseEntity<String> paginaDeDecisao(@RequestParam("t") String token) {
		Optional<ComentarioService.Fila> c = servico.porToken(token);
		if (c.isEmpty()) {
			return pagina("Link inválido", "<p>Este link não é válido ou expirou.</p>");
		}
		ComentarioService.Fila f = c.get();
		String texto = "<blockquote>" + HtmlUtils.htmlEscape(f.mensagem()).replace("\n", "<br>") + "</blockquote><p><b>"
				+ HtmlUtils.htmlEscape(f.nome()) + "</b> &lt;" + HtmlUtils.htmlEscape(f.email()) + "&gt;</p>";
		if (!"PENDENTE".equals(f.estado())) {
			return pagina("Comentário já decidido", texto + "<p>Este comentário já foi <b>" + rotulo(f.estado()) + "</b>.</p>");
		}
		String form = "<form method=\"post\" action=\"/api/public/comentarios/moderar\"><input type=\"hidden\" name=\"t\" value=\""
				+ HtmlUtils.htmlEscape(token) + "\"><button name=\"acao\" value=\"aprovar\" class=\"ok\">Aprovar e publicar no site</button> "
				+ "<button name=\"acao\" value=\"rejeitar\" class=\"nao\">Rejeitar</button></form>";
		return pagina("Moderar comentário do site", texto + form);
	}

	@PostMapping(value = "/api/public/comentarios/moderar", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
	ResponseEntity<String> decidir(@RequestParam("t") String token, @RequestParam("acao") String acao) {
		boolean aprovar = "aprovar".equals(acao);
		if (!aprovar && !"rejeitar".equals(acao)) {
			return pagina("Pedido inválido", "<p>Ação desconhecida.</p>");
		}
		return servico.decidirPorToken(token, aprovar)
				.map(estado -> pagina("Decisão registrada", "<p>O comentário está <b>" + rotulo(estado) + "</b>."
						+ ("APROVADO".equals(estado) ? " Ele já aparece no site." : "") + "</p>"))
				.orElseGet(() -> pagina("Link inválido", "<p>Este link não é válido ou expirou.</p>"));
	}

	@ExceptionHandler(ComentarioInvalidoException.class)
	ProblemDetail invalido(ComentarioInvalidoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	private static String rotulo(String estado) {
		return switch (estado) {
			case "APROVADO" -> "aprovado";
			case "REJEITADO" -> "rejeitado";
			default -> "pendente";
		};
	}

	private static String origem(HttpServletRequest req) {
		String xff = req.getHeader("X-Forwarded-For");
		String ip = xff == null || xff.isBlank() ? req.getRemoteAddr() : xff.split(",")[0].trim();
		return ip.length() > 64 ? ip.substring(0, 64) : ip;
	}

	private static ResponseEntity<String> pagina(String titulo, String corpo) {
		String html = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
				+ "<meta name=\"robots\" content=\"noindex\"><title>" + titulo + "</title><style>body{font-family:system-ui,sans-serif;max-width:36rem;margin:2rem auto;padding:0 1rem;"
				+ "color:#2a241c;background:#f6f1e8}blockquote{margin:1rem 0;padding:1rem;background:#fff;border-left:4px solid #c7a04b;border-radius:6px}"
				+ "button{font:inherit;padding:.7rem 1.1rem;border:0;border-radius:8px;cursor:pointer;color:#fff}.ok{background:#1b6e2d}.nao{background:#b3261e}</style></head><body><h1>"
				+ titulo + "</h1>" + corpo + "<p style=\"color:#6f665b;font-size:.85rem\">Crispim &amp; Campos Advogados</p></body></html>";
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(html);
	}
}
