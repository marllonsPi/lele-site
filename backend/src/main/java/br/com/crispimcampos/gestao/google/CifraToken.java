package br.com.crispimcampos.gestao.google;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM para segredos em repouso. A chave (32 bytes, base64) vem de variavel de ambiente, nunca do banco. O
 * dado associado (AAD) amarra o texto cifrado ao dono, entao um token copiado para outra linha nao decifra.
 * Formato: iv(12) + texto cifrado + tag.
 */
final class CifraToken {

	static final short VERSAO_CHAVE = 1;
	private static final int IV = 12;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKeySpec chave;

	CifraToken(String chaveBase64) {
		byte[] bruta;
		try {
			bruta = Base64.getDecoder().decode(chaveBase64.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("GOOGLE_TOKEN_KEY nao e base64 valido");
		}
		if (bruta.length != 32) {
			throw new IllegalArgumentException("GOOGLE_TOKEN_KEY deve ter 32 bytes (base64 de 32 bytes)");
		}
		this.chave = new SecretKeySpec(bruta, "AES");
	}

	byte[] cifrar(String texto, String aad) {
		try {
			byte[] iv = new byte[IV];
			RANDOM.nextBytes(iv);
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
			c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
			byte[] ct = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.allocate(IV + ct.length).put(iv).put(ct).array();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Falha ao cifrar", e);
		}
	}

	String decifrar(byte[] cifrado, String aad) {
		try {
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, cifrado, 0, IV));
			c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
			return new String(c.doFinal(cifrado, IV, cifrado.length - IV), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Falha ao decifrar (chave ou dono incorretos)");
		}
	}
}
