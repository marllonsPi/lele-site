package br.com.crispimcampos.gestao.seguranca;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de tentativas de login (arquitetura 9): apos {@link #MAXIMO} falhas em {@link #JANELA} para a mesma combinacao
 * IP + e-mail, bloqueia por {@link #BLOQUEIO}. Em memoria (uma instancia do backend); o e-mail nao e gravado em log.
 */
public class LoginThrottle {

	public static final int MAXIMO = 5;
	public static final Duration JANELA = Duration.ofMinutes(15);
	public static final Duration BLOQUEIO = Duration.ofMinutes(15);

	private final Clock relogio;
	private final Map<String, Deque<Instant>> falhas = new ConcurrentHashMap<>();
	private final Map<String, Instant> bloqueadoAte = new ConcurrentHashMap<>();

	public LoginThrottle(Clock relogio) {
		this.relogio = relogio;
	}

	static String chave(String ip, String email) {
		return (ip == null ? "?" : ip) + "|" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
	}

	public boolean bloqueado(String ip, String email) {
		String k = chave(ip, email);
		Instant ate = bloqueadoAte.get(k);
		if (ate == null) {
			return false;
		}
		if (relogio.instant().isAfter(ate)) {
			bloqueadoAte.remove(k);
			falhas.remove(k);
			return false;
		}
		return true;
	}

	public void falhou(String ip, String email) {
		String k = chave(ip, email);
		Instant agora = relogio.instant();
		Deque<Instant> fila = falhas.computeIfAbsent(k, x -> new ArrayDeque<>());
		synchronized (fila) {
			fila.addLast(agora);
			while (!fila.isEmpty() && fila.peekFirst().isBefore(agora.minus(JANELA))) {
				fila.pollFirst();
			}
			if (fila.size() >= MAXIMO) {
				bloqueadoAte.put(k, agora.plus(BLOQUEIO));
			}
		}
	}

	public void sucesso(String ip, String email) {
		String k = chave(ip, email);
		falhas.remove(k);
		bloqueadoAte.remove(k);
	}
}
