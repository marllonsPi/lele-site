package br.com.crispimcampos.gestao.agenda;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RelogioConfig {

	@Bean
	Clock relogio() {
		return Clock.system(ZoneId.of("America/Sao_Paulo"));
	}
}
