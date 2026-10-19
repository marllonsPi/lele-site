package br.com.crispimcampos.gestao.contas;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UsuarioDetailsService implements UserDetailsService {

	private final UsuarioRepository usuarios;

	UsuarioDetailsService(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		return usuarios.findByEmail(email.trim().toLowerCase())
				.map(UsuarioLogado::de)
				.orElseThrow(() -> new UsernameNotFoundException("Credenciais invalidas"));
	}
}
