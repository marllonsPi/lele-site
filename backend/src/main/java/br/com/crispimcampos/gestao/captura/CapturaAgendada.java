package br.com.crispimcampos.gestao.captura;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Captura diaria. Desligada por padrao: ligue com {@code app.captura.agendada=true}. Hoje assume UMA instancia do backend
 * (sem trava distribuida); a janela de 3 dias e segura porque cada passada e idempotente.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.captura.agendada", havingValue = "true")
class CapturaAgendada {

	private static final Logger log = LoggerFactory.getLogger(CapturaAgendada.class);

	private final CapturaService captura;

	CapturaAgendada(CapturaService captura) {
		this.captura = captura;
	}

	@Scheduled(cron = "${app.captura.cron:0 0 6 * * *}", zone = "America/Sao_Paulo")
	void executarDiaria() {
		LocalDate hoje = LocalDate.now();
		var r = captura.executar(null, hoje.minusDays(3), hoje);
		r.stream().filter(e -> "ERRO".equals(e.resultado())).forEach(e -> log.error("Captura {} com erro: {}", e.fonte(), e.erro()));
	}
}
