package br.com.crispimcampos.gestao.alertas;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Alertas diarios. Desligado por padrao: {@code app.alertas.agendado=true}. Assume uma instancia do backend. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.alertas.agendado", havingValue = "true")
class AlertaAgendado {

	private final AlertaService servico;

	AlertaAgendado(AlertaService servico) {
		this.servico = servico;
	}

	@Scheduled(cron = "${app.alertas.cron:0 0 7 * * *}", zone = "America/Sao_Paulo")
	void diario() {
		servico.executar(null);
	}
}
