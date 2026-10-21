package br.com.crispimcampos.gestao.google;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Chamadas HTTP ao Google (OAuth, userinfo, Gmail). Sem SDK: a VM tem 1 GB. Nunca registra token em log. */
@Component
class GoogleApi {

	record Tokens(String acesso, String refresh, long expiraEmSegundos, String escopos) {
	}

	private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
	};

	private final RestClient http;
	private final String tokenUrl;
	private final String revogarUrl;
	private final String userinfoUrl;
	private final String gmailUrl;
	private final String driveUrl;

	GoogleApi(@Value("${app.google.token-url:https://oauth2.googleapis.com/token}") String tokenUrl,
			@Value("${app.google.revoke-url:https://oauth2.googleapis.com/revoke}") String revogarUrl,
			@Value("${app.google.userinfo-url:https://openidconnect.googleapis.com/v1/userinfo}") String userinfoUrl,
			@Value("${app.google.gmail-url:https://gmail.googleapis.com}") String gmailUrl,
			@Value("${app.google.drive-url:https://www.googleapis.com}") String driveUrl) {
		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		fabrica.setReadTimeout(Duration.ofSeconds(30));
		this.http = RestClient.builder().requestFactory(fabrica).build();
		this.tokenUrl = tokenUrl;
		this.revogarUrl = revogarUrl;
		this.userinfoUrl = userinfoUrl;
		this.gmailUrl = gmailUrl;
		this.driveUrl = driveUrl;
	}

	Tokens trocarCodigo(String clientId, String segredo, String redirect, String code, String verifier) {
		var f = new LinkedMultiValueMap<String, String>();
		f.add("grant_type", "authorization_code");
		f.add("code", code);
		f.add("redirect_uri", redirect);
		f.add("client_id", clientId);
		f.add("client_secret", segredo);
		f.add("code_verifier", verifier);
		return tokens(f);
	}

	Tokens renovar(String clientId, String segredo, String refresh) {
		var f = new LinkedMultiValueMap<String, String>();
		f.add("grant_type", "refresh_token");
		f.add("refresh_token", refresh);
		f.add("client_id", clientId);
		f.add("client_secret", segredo);
		return tokens(f);
	}

	private Tokens tokens(LinkedMultiValueMap<String, String> form) {
		try {
			Map<String, Object> r = http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
					.retrieve().body(MAPA);
			if (r == null || r.get("access_token") == null) {
				throw new ConexaoGoogleException("Resposta inesperada do Google");
			}
			return new Tokens(String.valueOf(r.get("access_token")), r.get("refresh_token") == null ? null : String.valueOf(r.get("refresh_token")),
					r.get("expires_in") == null ? 3000 : Long.parseLong(String.valueOf(r.get("expires_in"))),
					r.get("scope") == null ? "" : String.valueOf(r.get("scope")));
		} catch (HttpClientErrorException e) {
			boolean grant = e.getResponseBodyAsString().contains("invalid_grant");
			throw new ConexaoGoogleException(grant ? "Autorizacao recusada ou expirada pelo Google" : "O Google recusou a requisicao", grant);
		}
	}

	String email(String acesso) {
		Map<String, Object> r = http.get().uri(userinfoUrl).headers(h -> h.setBearerAuth(acesso)).retrieve().body(MAPA);
		if (r == null || r.get("email") == null) {
			throw new ConexaoGoogleException("Nao foi possivel identificar a conta Google");
		}
		return String.valueOf(r.get("email"));
	}

	/** Melhor esforco: se o Google nao conseguir revogar, a conexao local e apagada do mesmo jeito. */
	boolean revogar(String token) {
		try {
			var f = new LinkedMultiValueMap<String, String>();
			f.add("token", token);
			http.post().uri(revogarUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(f).retrieve().toBodilessEntity();
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	List<MensagemGmail> mensagens(String acesso, String consulta, int maximo) {
		List<String> ids = new ArrayList<>();
		String pagina = null;
		do {
			final String pt = pagina;
			Map<String, Object> r = http.get().uri(gmailUrl + "/gmail/v1/users/me/messages?q={q}&maxResults=100"
					+ (pt == null ? "" : "&pageToken={p}"), pt == null ? new Object[] { consulta } : new Object[] { consulta, pt })
					.headers(h -> h.setBearerAuth(acesso)).retrieve().body(MAPA);
			if (r == null) {
				throw new ConexaoGoogleException("Resposta inesperada do Gmail");
			}
			if (r.get("messages") instanceof List<?> ms) {
				for (Object m : ms) {
					if (m instanceof Map<?, ?> mm && ids.size() < maximo) {
						ids.add(String.valueOf(mm.get("id")));
					}
				}
			}
			pagina = r.get("nextPageToken") == null ? null : String.valueOf(r.get("nextPageToken"));
		} while (pagina != null && ids.size() < maximo);
		List<MensagemGmail> saida = new ArrayList<>();
		for (String id : ids) {
			Map<String, Object> m = http.get().uri(gmailUrl + "/gmail/v1/users/me/messages/{id}?format=full", id)
					.headers(h -> h.setBearerAuth(acesso)).retrieve().body(MAPA);
			if (m != null) {
				saida.add(MensagemGmail.de(m));
			}
		}
		return saida;
	}

	// ---- Drive

	/** Item de pasta do Drive. {@code pasta}: e uma pasta (mimeType application/vnd.google-apps.folder). */
	record ItemDrive(String id, String nome, String mime, java.time.Instant modificadoEm, long tamanho, boolean pasta) {
	}

	List<ItemDrive> filhos(String acesso, String pastaId) {
		List<ItemDrive> saida = new ArrayList<>();
		String pagina = null;
		do {
			final String pt = pagina;
			String q = "'" + pastaId.replaceAll("[^A-Za-z0-9_-]", "") + "' in parents and trashed = false";
			Map<String, Object> r = http.get().uri(driveUrl + "/drive/v3/files?q={q}&pageSize=200&fields={f}&supportsAllDrives=true&includeItemsFromAllDrives=true"
					+ (pt == null ? "" : "&pageToken={p}"), pt == null ? new Object[] { q, "nextPageToken,files(id,name,mimeType,modifiedTime,size)" }
							: new Object[] { q, "nextPageToken,files(id,name,mimeType,modifiedTime,size)", pt })
					.headers(h -> h.setBearerAuth(acesso)).retrieve().body(MAPA);
			if (r == null) {
				throw new ConexaoGoogleException("Resposta inesperada do Drive");
			}
			if (r.get("files") instanceof List<?> fs) {
				for (Object o : fs) {
					if (o instanceof Map<?, ?> m) {
						String mime = String.valueOf(m.get("mimeType"));
						saida.add(new ItemDrive(String.valueOf(m.get("id")), String.valueOf(m.get("name")), mime,
								m.get("modifiedTime") == null ? null : java.time.Instant.parse(String.valueOf(m.get("modifiedTime"))),
								m.get("size") == null ? 0 : Long.parseLong(String.valueOf(m.get("size"))), mime.equals("application/vnd.google-apps.folder")));
					}
				}
			}
			pagina = r.get("nextPageToken") == null ? null : String.valueOf(r.get("nextPageToken"));
		} while (pagina != null);
		return saida;
	}

	/** Conteudo binario de um arquivo (alt=media). */
	byte[] baixar(String acesso, String arquivoId) {
		return http.get().uri(driveUrl + "/drive/v3/files/{id}?alt=media&supportsAllDrives=true", arquivoId).headers(h -> h.setBearerAuth(acesso))
				.retrieve().body(byte[].class);
	}

	/** Exporta um arquivo nativo do Google (Docs, Sheets) para o tipo pedido. */
	byte[] exportar(String acesso, String arquivoId, String mimeDestino) {
		return http.get().uri(driveUrl + "/drive/v3/files/{id}/export?mimeType={m}", arquivoId, mimeDestino).headers(h -> h.setBearerAuth(acesso))
				.retrieve().body(byte[].class);
	}

	/** Pastas filhas de uma pasta (para achar pasta por nome sem criar duplicada). */
	List<ItemDrive> pastasFilhas(String acesso, String paiId) {
		return filhos(acesso, paiId).stream().filter(ItemDrive::pasta).toList();
	}

	/** Cria uma pasta dentro de {@code paiId}; devolve o id. */
	String criarPasta(String acesso, String paiId, String nome) {
		Map<String, Object> corpo = Map.of("name", nome, "mimeType", "application/vnd.google-apps.folder", "parents", List.of(paiId));
		Map<String, Object> r = http.post().uri(driveUrl + "/drive/v3/files?supportsAllDrives=true&fields=id").headers(h -> h.setBearerAuth(acesso))
				.contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(MAPA);
		if (r == null || r.get("id") == null) {
			throw new ConexaoGoogleException("O Drive nao confirmou a criacao da pasta");
		}
		return String.valueOf(r.get("id"));
	}

	/** Envia um arquivo (ate ~20 MB, upload multipart) para a pasta; devolve o id do arquivo. */
	String enviarArquivo(String acesso, String paiId, String nome, String mime, byte[] conteudo) {
		String limite = "crispim" + java.util.UUID.randomUUID().toString().replace("-", "");
		String meta = "{\"name\":" + jsonTexto(nome) + ",\"parents\":[" + jsonTexto(paiId) + "]}";
		String tipo = mime == null || mime.isBlank() ? "application/octet-stream" : mime;
		var saida = new java.io.ByteArrayOutputStream();
		byte[] cabecalho = ("--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta + "\r\n--" + limite + "\r\nContent-Type: " + tipo + "\r\n\r\n")
				.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		saida.writeBytes(cabecalho);
		saida.writeBytes(conteudo);
		saida.writeBytes(("\r\n--" + limite + "--").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Map<String, Object> r = http.post().uri(driveUrl + "/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true&fields=id")
				.headers(h -> h.setBearerAuth(acesso)).contentType(MediaType.parseMediaType("multipart/related; boundary=" + limite))
				.body(saida.toByteArray()).retrieve().body(MAPA);
		if (r == null || r.get("id") == null) {
			throw new ConexaoGoogleException("O Drive nao confirmou o envio do arquivo");
		}
		return String.valueOf(r.get("id"));
	}

	/** Texto JSON escapado (aspas, barras e controles), sem depender de biblioteca. */
	static String jsonTexto(String v) {
		StringBuilder b = new StringBuilder("\"");
		for (char c : v.toCharArray()) {
			switch (c) {
				case '"' -> b.append("\\\"");
				case '\\' -> b.append("\\\\");
				case '\n' -> b.append("\\n");
				case '\r' -> b.append("\\r");
				case '\t' -> b.append("\\t");
				default -> {
					if (c < 0x20) {
						b.append(String.format("\\u%04x", (int) c));
					} else {
						b.append(c);
					}
				}
			}
		}
		return b.append('"').toString();
	}
}
