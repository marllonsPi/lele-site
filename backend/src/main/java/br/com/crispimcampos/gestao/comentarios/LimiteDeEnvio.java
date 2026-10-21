package br.com.crispimcampos.gestao.comentarios;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Limite simples por origem (IP informado pelo proxy): poucos comentarios por hora, em memoria. */
@Component
class LimiteDeEnvio {

	static final int MAXIMO_POR_HORA = 3;
	private static final Duration JANELA = Duration.ofHours(1);

	private final Clock relogio;
	private final Map<String, Deque<Instant>> porOrigem = new HashMap<>();

	LimiteDeEnvio(Clock relogio) {
		this.relogio = relogio;
	}

	/** @return true se pode enviar (e ja conta este envio) */
	synchronized boolean permitir(String origem) {
		Instant agora = relogio.instant();
		if (porOrigem.size() > 5000) {
			porOrigem.values().removeIf(d -> d.isEmpty() || d.peekLast().isBefore(agora.minus(JANELA)));
		}
		Deque<Instant> d = porOrigem.computeIfAbsent(origem, k -> new ArrayDeque<>());
		while (!d.isEmpty() && d.peekFirst().isBefore(agora.minus(JANELA))) {
			d.pollFirst();
		}
		if (d.size() >= MAXIMO_POR_HORA) {
			return false;
		}
		d.addLast(agora);
		return true;
	}
}
