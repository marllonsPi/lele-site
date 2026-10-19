package br.com.crispimcampos.gestao.dev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Libera so a pagina estatica do console; a API continua exigindo login. Existe apenas no perfil dev. */
@Configuration
@Profile("dev")
class DevSecurity {

	@Bean
	@Order(0)
	SecurityFilterChain consoleDev(HttpSecurity http) throws Exception {
		http.securityMatcher("/dev/**").authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable());
		return http.build();
	}
}
