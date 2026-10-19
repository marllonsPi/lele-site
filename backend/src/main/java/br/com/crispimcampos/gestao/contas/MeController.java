package br.com.crispimcampos.gestao.contas;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

	record Eu(Long id, String nome, String email, Perfil perfil, boolean mestre) {
	}

	@GetMapping("/api/me")
	Eu eu(@AuthenticationPrincipal UsuarioLogado usuario) {
		return new Eu(usuario.id(), usuario.nome(), usuario.email(), usuario.perfil(), usuario.mestre());
	}
}
