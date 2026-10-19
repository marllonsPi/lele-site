package br.com.crispimcampos.gestao.seguranca;

import br.com.crispimcampos.gestao.contas.DoisFatoresService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Confere a senha como de costume e, se o usuario ativou o 2FA, tambem o codigo (parametro {@code codigo} do login). */
class ProvedorComDoisFatores extends DaoAuthenticationProvider {

	private final DoisFatoresService doisFatores;

	ProvedorComDoisFatores(UserDetailsService usuarios, DoisFatoresService doisFatores) {
		super(usuarios);
		this.doisFatores = doisFatores;
	}

	@Override
	protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken autenticacao)
			throws AuthenticationException {
		super.additionalAuthenticationChecks(userDetails, autenticacao); // senha primeiro
		String email = userDetails.getUsername();
		if (doisFatores == null || !doisFatores.exigeCodigo(email)) {
			return;
		}
		String codigo = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
				? a.getRequest().getParameter("codigo") : null;
		if (codigo == null || codigo.isBlank()) {
			throw new DoisFatoresObrigatorioException();
		}
		if (!doisFatores.codigoValido(email, codigo.trim())) {
			throw new BadCredentialsException("Credenciais invalidas");
		}
	}
}
