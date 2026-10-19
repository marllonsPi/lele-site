package br.com.crispimcampos.gestao.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login individual por sessao (cookie HttpOnly) para a SPA. Resposta de API sem redirecionamento:
 * 204 no sucesso, 401 na falha. Todo o restante exige autenticacao.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	SecurityFilterChain filtros(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
				.authorizeHttpRequests(a -> a
						.requestMatchers("/actuator/health/**", "/api/csrf").permitAll()
						.anyRequest().authenticated())
				.formLogin(f -> f
						.loginProcessingUrl("/api/login")
						.usernameParameter("email")
						.passwordParameter("senha")
						.successHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
						.failureHandler((req, res, ex) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
				.logout(l -> l
						.logoutUrl("/api/logout")
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.headers(h -> h.frameOptions(f -> f.deny()))
				.httpBasic(b -> b.disable());
		return http.build();
	}

	@RestController
	static class CsrfController {

		/** A SPA le o token (cookie XSRF-TOKEN) e o devolve no cabecalho X-XSRF-TOKEN. */
		@GetMapping("/api/csrf")
		void csrf(CsrfToken token) {
			token.getToken();
		}
	}
}
