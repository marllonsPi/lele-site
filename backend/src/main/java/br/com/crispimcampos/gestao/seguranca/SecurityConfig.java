package br.com.crispimcampos.gestao.seguranca;

import br.com.crispimcampos.gestao.contas.DoisFatoresService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Login individual por sessao (cookie HttpOnly) para a SPA. Resposta de API sem redirecionamento:
 * 204 no sucesso, 401 na falha. Todo o restante exige autenticacao.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	LoginThrottle loginThrottle(org.springframework.beans.factory.ObjectProvider<Clock> relogio) {
		return new LoginThrottle(relogio.getIfAvailable(Clock::systemUTC));
	}

	@Bean
	AuthenticationProvider provedor(UserDetailsService usuarios, PasswordEncoder encoder,
			org.springframework.beans.factory.ObjectProvider<DoisFatoresService> doisFatores) {
		// Em producao o servico sempre existe; so fatias de teste sem o modulo `contas` completo ficam sem 2FA.
		ProvedorComDoisFatores p = new ProvedorComDoisFatores(usuarios, doisFatores.getIfAvailable());
		p.setPasswordEncoder(encoder);
		return p;
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	@Bean
	SecurityFilterChain filtros(HttpSecurity http, CsrfTokenRepository csrfTokenRepository, LoginThrottle throttle) throws Exception {
		http
				// A SPA devolve o valor bruto do cookie no cabecalho; o handler padrao (Xor) exigiria o token mascarado.
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
						// formulario do site e link de moderacao: sem sessao, protegidos por limite, isca e token
						.ignoringRequestMatchers("/api/public/**"))
				.authorizeHttpRequests(a -> a
						.requestMatchers("/actuator/health/**", "/api/csrf", "/api/public/**").permitAll()
						.anyRequest().authenticated())
				.formLogin(f -> f
						.loginProcessingUrl("/api/login")
						.usernameParameter("email")
						.passwordParameter("senha")
						.successHandler((req, res, auth) -> {
							throttle.sucesso(req.getRemoteAddr(), req.getParameter("email"));
							res.setStatus(HttpStatus.NO_CONTENT.value());
						})
						.failureHandler((req, res, ex) -> {
							if (ex instanceof DoisFatoresObrigatorioException) {
								// senha correta: pedir o codigo nao conta como tentativa errada
								res.setHeader("X-2FA-Obrigatorio", "true");
							} else {
								throttle.falhou(req.getRemoteAddr(), req.getParameter("email"));
							}
							res.setStatus(HttpStatus.UNAUTHORIZED.value());
						}))
				.addFilterBefore(new LimiteDeLogin(throttle), UsernamePasswordAuthenticationFilter.class)
				.logout(l -> l
						.logoutUrl("/api/logout")
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.headers(h -> h
						.frameOptions(f -> f.deny())
						.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
								+ "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
						.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
				.httpBasic(b -> b.disable());
		return http.build();
	}

	/** Responde 429 a tentativas de login enquanto a combinacao IP + e-mail estiver bloqueada. */
	static class LimiteDeLogin extends OncePerRequestFilter {

		private final LoginThrottle throttle;

		LimiteDeLogin(LoginThrottle throttle) {
			this.throttle = throttle;
		}

		@Override
		protected boolean shouldNotFilter(HttpServletRequest request) {
			return !("POST".equals(request.getMethod()) && request.getRequestURI().endsWith("/api/login"));
		}

		@Override
		protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
			if (throttle.bloqueado(req.getRemoteAddr(), req.getParameter("email"))) {
				res.setHeader("Retry-After", String.valueOf(LoginThrottle.BLOQUEIO.toSeconds()));
				res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
				return;
			}
			chain.doFilter(req, res);
		}
	}

	@RestController
	static class CsrfEndpoint {

		private final CsrfTokenRepository repositorio;

		CsrfEndpoint(CsrfTokenRepository repositorio) {
			this.repositorio = repositorio;
		}

		/** A SPA le o token (cookie XSRF-TOKEN) e o devolve no cabecalho X-XSRF-TOKEN. */
		@GetMapping("/api/csrf")
		void csrf(HttpServletRequest request, HttpServletResponse response) {
			if (repositorio.loadToken(request) == null) {
				repositorio.saveToken(repositorio.generateToken(request), request, response);
			}
		}
	}
}
