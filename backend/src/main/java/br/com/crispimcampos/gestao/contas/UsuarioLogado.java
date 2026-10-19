package br.com.crispimcampos.gestao.contas;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Identidade na sessao: dados minimos, sem entidade JPA. */
public record UsuarioLogado(Long id, String nome, String email, String senhaHash, Perfil perfil, boolean ativo)
		implements UserDetails {

	public static UsuarioLogado de(Usuario u) {
		return new UsuarioLogado(u.getId(), u.getNome(), u.getEmail(), u.getSenhaHash(), u.getPerfil(), u.isAtivo());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(perfil.papel()));
	}

	@Override
	public String getPassword() {
		return senhaHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return ativo;
	}
}
