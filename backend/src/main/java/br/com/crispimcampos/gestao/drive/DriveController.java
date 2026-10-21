package br.com.crispimcampos.gestao.drive;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.google.GoogleContasService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Configuracao do Drive do escritorio (somente socias). */
@RestController
@PreAuthorize("hasRole('SOCIA')")
class DriveController {

	record Configuracao(Boolean usarMinhaConta, String pastaRaiz) {
	}

	private final DriveEscritorio drive;
	private final GoogleContasService google;

	DriveController(DriveEscritorio drive, GoogleContasService google) {
		this.drive = drive;
		this.google = google;
	}

	@GetMapping("/api/drive/status")
	Map<String, Object> status(@AuthenticationPrincipal UsuarioLogado eu) {
		var c = drive.config();
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("googleDisponivel", google.disponivel());
		r.put("minhaContaComDrive", google.status(eu.id()).drive());
		r.put("contaDoDriveDefinida", c.contaUsuarioId() != null);
		r.put("euSouAContaDoDrive", eu.id().equals(c.contaUsuarioId()));
		r.put("contaDoDriveConectada", c.contaUsuarioId() != null && google.status(c.contaUsuarioId()).drive());
		r.put("pastaRaizId", c.pastaRaizId());
		r.put("ativo", drive.ativo());
		return r;
	}

	@PutMapping("/api/drive/config")
	Map<String, Object> definir(@AuthenticationPrincipal UsuarioLogado eu, @RequestBody Configuracao c) {
		Long conta = Boolean.TRUE.equals(c.usarMinhaConta()) ? eu.id() : drive.config().contaUsuarioId();
		drive.definir(conta, c.pastaRaiz());
		return status(eu);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalido(IllegalArgumentException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}
}
