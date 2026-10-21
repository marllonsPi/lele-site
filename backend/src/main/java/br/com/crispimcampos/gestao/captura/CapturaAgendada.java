package br.com.crispimcampos.gestao.captura;

import br.com.crispimcampos.gestao.sistema.ConfiguracaoService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Captura diaria com interruptor na tela (chave {@code captura.agendada}, desligada por padrao). O agendador sempre
 * existe; a cada disparo ele confere o interruptor, entao ligar/desligar vale na hora, sem reiniciar. Assume UMA
 * instancia do backend (sem trava distribuida); a janela de 3 dias e segura porque cada passada e idempotente.
 */
@Configuration
@EnableScheduling
class CapturaAgendada {

	static final String CHAVE = "captura.agendada";

	private static final Logger log = LoggerFactory.getLogger(CapturaAgendada.class);

	private final CapturaService captura;
	private final ConfiguracaoService configuracao;
	private final String cron;

	CapturaAgendada(CapturaService captura, ConfiguracaoService configuracao,
			@Value("${app.captura.cron:0 0 6 * * *}") String cron) {
		this.captura = captura;
		this.configuracao = configuracao;
		this.cron = cron;
	}

	boolean ativa() {
		return "true".equalsIgnoreCase(configuracao.valor(CHAVE, "false"));
	}

	void definir(Long atorId, boolean ligada) {
		configuracao.definirInterna(atorId, CHAVE, String.valueOf(ligada));
	}

	String cron() {
		return cron;
	}

	@Scheduled(cron = "${app.captura.cron:0 0 6 * * *}", zone = "America/Sao_Paulo")
	void executarDiaria() {
		if (!ativa()) {
			return;
		}
		LocalDate hoje = LocalDate.now();
		var r = captura.executar(null, hoje.minusDays(3), hoje);
		r.stream().filter(e -> "ERRO".equals(e.resultado())).forEach(e -> log.error("Captura {} com erro: {}", e.fonte(), e.erro()));
	}
}
