package br.com.crispimcampos.gestao.contas;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** TOTP (RFC 6238, HMAC-SHA1, 6 digitos, passo de 30 s), compativel com Google Authenticator, Authy e similares. Dominio puro. */
public final class Totp {

	static final int PASSO_SEGUNDOS = 30;
	static final int DIGITOS = 6;
	/** Tolera relogio ligeiramente desencontrado: aceita o passo anterior e o seguinte. */
	static final int JANELA = 1;
	private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private Totp() {
	}

	public static String novoSegredo() {
		byte[] b = new byte[20];
		new SecureRandom().nextBytes(b);
		return base32(b);
	}

	public static String codigo(byte[] segredo, long instanteEmSegundos) {
		long contador = instanteEmSegundos / PASSO_SEGUNDOS;
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(segredo, "HmacSHA1"));
			byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(contador).array());
			int o = h[h.length - 1] & 0x0F;
			int bin = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
			return String.format("%0" + DIGITOS + "d", bin % (int) Math.pow(10, DIGITOS));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Codigo do instante informado (para testes e ferramentas de suporte). */
	public static String codigoAtual(String segredoBase32, Instant agora) {
		return codigo(decodificar(segredoBase32), agora.getEpochSecond());
	}

	public static boolean valido(String segredoBase32, String informado, Instant agora) {
		if (informado == null || !informado.matches("\\d{" + DIGITOS + "}")) {
			return false;
		}
		byte[] segredo = decodificar(segredoBase32);
		boolean ok = false;
		for (int d = -JANELA; d <= JANELA; d++) {
			String esperado = codigo(segredo, agora.getEpochSecond() + (long) d * PASSO_SEGUNDOS);
			ok |= MessageDigest.isEqual(esperado.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
					informado.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		}
		return ok;
	}

	/** URI para QR code / cadastro manual no aplicativo autenticador. */
	public static String uri(String emissor, String conta, String segredoBase32) {
		return "otpauth://totp/" + java.net.URLEncoder.encode(emissor + ":" + conta, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")
				+ "?secret=" + segredoBase32 + "&issuer=" + java.net.URLEncoder.encode(emissor, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")
				+ "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASSO_SEGUNDOS;
	}

	static String base32(byte[] dados) {
		StringBuilder sb = new StringBuilder();
		int buffer = 0;
		int bits = 0;
		for (byte b : dados) {
			buffer = (buffer << 8) | (b & 0xFF);
			bits += 8;
			while (bits >= 5) {
				sb.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
				bits -= 5;
			}
		}
		if (bits > 0) {
			sb.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
		}
		return sb.toString();
	}

	static byte[] decodificar(String base32) {
		String s = base32.replace("=", "").replace(" ", "").toUpperCase();
		java.io.ByteArrayOutputStream saida = new java.io.ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (char c : s.toCharArray()) {
			int v = ALFABETO.indexOf(c);
			if (v < 0) {
				throw new IllegalArgumentException("Segredo invalido");
			}
			buffer = (buffer << 5) | v;
			bits += 5;
			if (bits >= 8) {
				saida.write((buffer >> (bits - 8)) & 0xFF);
				bits -= 8;
			}
		}
		return saida.toByteArray();
	}
}
