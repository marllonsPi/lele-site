package br.com.crispimcampos.gestao.google;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Conexao da conta Google de cada pessoa por OAuth 2.0 (code + PKCE). Escopos minimos: identificacao (openid, email) e
 * leitura do Gmail. Nunca guarda senha; o refresh token fica cifrado (AES-GCM) com chave em variavel de ambiente.
 * Todo metodo opera so sobre o usuario informado (invariantes 1 e 9); nao ha leitura da conexao de outra pessoa.
 */
@Service
public class GoogleContasService {

	static final String ESCOPO_GMAIL = "https://www.googleapis.com/auth/gmail.readonly";
	/** Drive completo: o sistema le as pastas dos clientes e GRAVA os anexos no Drive do escritorio (criar pastas e arquivos). */
	static final String ESCOPO_DRIVE = "https://www.googleapis.com/auth/drive";
	static final String ESCOPOS = "openid email " + ESCOPO_GMAIL;
	private static final Logger log = LoggerFactory.getLogger(GoogleContasService.class);
	private static final SecureRandom RANDOM = new SecureRandom();

	/** {@code drive}: a conexao inclui leitura do Drive (opt-in, usado na importacao de pastas de clientes e da planilha). */
	public record Status(boolean disponivel, boolean conectado, String estado, String email, Instant desde, boolean drive) {
	}

	private record AcessoEmCache(String token, Instant expira) {
	}

	private final GoogleApi api;
	private final JdbcTemplate jdbc;
	private final AuditoriaService auditoria;
	private final Clock relogio;
	private final String clientId;
	private final String segredo;
	private final String redirect;
	private final String authUrl;
	private final CifraToken cifra;
	private final Map<Long, AcessoEmCache> cache = new ConcurrentHashMap<>();

	GoogleContasService(GoogleApi api, JdbcTemplate jdbc, AuditoriaService auditoria, Clock relogio,
			@Value("${app.google.client-id:}") String clientId, @Value("${app.google.client-secret:}") String segredo,
			@Value("${app.google.redirect-uri:}") String redirect, @Value("${app.google.token-key:}") String chave,
			@Value("${app.google.auth-url:https://accounts.google.com/o/oauth2/v2/auth}") String authUrl) {
		this.api = api;
		this.jdbc = jdbc;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.clientId = clientId;
		this.segredo = segredo;
		this.redirect = redirect;
		this.authUrl = authUrl;
		this.cifra = chave.isBlank() ? null : new CifraToken(chave);
		if (!disponivel()) {
			log.info("Conexao Google desligada (GOOGLE_CLIENT_ID/SECRET/REDIRECT_URI/TOKEN_KEY nao configurados)");
		}
	}

	public boolean disponivel() {
		return cifra != null && !clientId.isBlank() && !segredo.isBlank() && !redirect.isBlank();
	}

	private void exigirDisponivel() {
		if (!disponivel()) {
			throw new ConexaoGoogleException("Conexao com o Google nao esta configurada neste ambiente");
		}
	}

	public Status status(Long usuarioId) {
		var linhas = jdbc.query("select estado, email_google, criada_em, escopos from conexao_google where usuario_id = ?",
				(rs, n) -> new Status(disponivel(), true, rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
						temEscopo(rs.getString(4), ESCOPO_DRIVE)), usuarioId);
		return linhas.isEmpty() ? new Status(disponivel(), false, null, null, null, false) : linhas.get(0);
	}

	/** Gera o estado e o desafio PKCE e devolve a URL de consentimento do Google. */
	@Transactional
	public String iniciar(Long usuarioId, String emailDica) {
		return iniciar(usuarioId, emailDica, false);
	}

	/** Com {@code drive}, pede tambem o acesso ao Google Drive (ler as pastas e gravar os anexos). */
	@Transactional
	public String iniciar(Long usuarioId, String emailDica, boolean drive) {
		exigirDisponivel();
		jdbc.update("delete from oauth_estado where expira_em < ?", agora());
		String state = aleatorio(32);
		String verifier = aleatorio(48);
		jdbc.update("insert into oauth_estado (state_hash, usuario_id, verifier_cifrado, expira_em) values (?, ?, ?, ?)",
				sha256(state), usuarioId, cifra.cifrar(verifier, "oauth_estado:" + usuarioId), agora().plusMinutes(10));
		return UriComponentsBuilder.fromUriString(authUrl)
				.queryParam("client_id", clientId).queryParam("redirect_uri", redirect).queryParam("response_type", "code")
				.queryParam("scope", drive ? ESCOPOS + " " + ESCOPO_DRIVE : ESCOPOS).queryParam("access_type", "offline").queryParam("prompt", "consent")
				.queryParam("include_granted_scopes", "false").queryParam("state", state)
				.queryParam("code_challenge", sha256Base64Url(verifier)).queryParam("code_challenge_method", "S256")
				.queryParam("login_hint", emailDica)
				.encode().build().toUriString();
	}

	/** Conclui o consentimento: valida o estado (uso unico, 10 min, mesmo usuario), troca o codigo e guarda o refresh token cifrado. */
	@Transactional
	public void concluir(Long usuarioId, String code, String state) {
		exigirDisponivel();
		if (code == null || code.isBlank() || state == null || state.isBlank()) {
			throw new ConexaoGoogleException("Retorno do Google incompleto");
		}
		var linhas = jdbc.query("select usuario_id, verifier_cifrado, expira_em from oauth_estado where state_hash = ?",
				(rs, n) -> new Object[] { rs.getLong(1), rs.getBytes(2), rs.getTimestamp(3).toInstant() }, sha256(state));
		jdbc.update("delete from oauth_estado where state_hash = ?", sha256(state));
		if (linhas.isEmpty() || !usuarioId.equals(linhas.get(0)[0]) || ((Instant) linhas.get(0)[2]).isBefore(relogio.instant())) {
			throw new ConexaoGoogleException("Pedido de conexao invalido ou expirado; tente novamente");
		}
		String verifier = cifra.decifrar((byte[]) linhas.get(0)[1], "oauth_estado:" + usuarioId);
		GoogleApi.Tokens t = api.trocarCodigo(clientId, segredo, redirect, code, verifier);
		if (t.refresh() == null || t.refresh().isBlank()) {
			throw new ConexaoGoogleException("O Google nao liberou acesso continuo; remova o acesso do app na conta Google e conecte de novo");
		}
		if (!t.escopos().contains(ESCOPO_GMAIL)) {
			throw new ConexaoGoogleException("E necessario permitir a leitura do Gmail para conectar");
		}
		String email = api.email(t.acesso());
		jdbc.update("""
				insert into conexao_google (usuario_id, email_google, refresh_token_cifrado, versao_chave, escopos, estado)
				values (?, ?, ?, ?, ?, 'ATIVA')
				on conflict (usuario_id) do update set email_google = excluded.email_google,
				  refresh_token_cifrado = excluded.refresh_token_cifrado, versao_chave = excluded.versao_chave,
				  escopos = excluded.escopos, estado = 'ATIVA', criada_em = now(), ultimo_uso_em = null
				""", usuarioId, email, cifra.cifrar(t.refresh(), aad(usuarioId)), CifraToken.VERSAO_CHAVE, t.escopos());
		cache.put(usuarioId, new AcessoEmCache(t.acesso(), relogio.instant().plusSeconds(Math.max(60, t.expiraEmSegundos() - 60))));
		auditoria.registrar(usuarioId, "GOOGLE_CONECTAR", "conexao_google", String.valueOf(usuarioId),
				temEscopo(t.escopos(), ESCOPO_DRIVE) ? "escopo=gmail.readonly+drive" : "escopo=gmail.readonly");
	}

	/** Desconexao pela propria pessoa: revoga no Google e apaga o token local, mesmo se a revogacao falhar. */
	@Transactional
	public boolean desconectar(Long usuarioId) {
		var cifrados = jdbc.query("select refresh_token_cifrado from conexao_google where usuario_id = ?", (rs, n) -> rs.getBytes(1), usuarioId);
		cache.remove(usuarioId);
		if (cifrados.isEmpty()) {
			return false;
		}
		boolean revogado = false;
		if (cifra != null) {
			try {
				revogado = api.revogar(cifra.decifrar(cifrados.get(0), aad(usuarioId)));
			} catch (RuntimeException e) {
				log.warn("Token Google ilegivel ao desconectar (usuario {})", usuarioId);
			}
		}
		jdbc.update("delete from conexao_google where usuario_id = ?", usuarioId);
		auditoria.registrar(usuarioId, "GOOGLE_DESCONECTAR", "conexao_google", String.valueOf(usuarioId), revogado ? "revogado=sim" : "revogado=nao");
		return revogado;
	}

	/** Usuarios com conexao ATIVA (quem a captura por e-mail deve consultar). */
	public List<Long> usuariosConectados() {
		if (!disponivel()) {
			return List.of();
		}
		return jdbc.queryForList("select usuario_id from conexao_google where estado = 'ATIVA' order by usuario_id", Long.class);
	}

	/** Mensagens do Gmail do proprio usuario que casam com a consulta. Marca a conexao EXPIRADA se o Google recusar o token. */
	public List<MensagemGmail> mensagens(Long usuarioId, String consulta, int maximo) {
		String acesso = tokenDeAcesso(usuarioId);
		List<MensagemGmail> r = api.mensagens(acesso, consulta, maximo);
		jdbc.update("update conexao_google set ultimo_uso_em = now() where usuario_id = ?", usuarioId);
		return r;
	}

	/** Escopos vem separados por espaco; compara inteiro (drive.readonly nao conta como drive). */
	static boolean temEscopo(String escopos, String escopo) {
		return escopos != null && java.util.Arrays.asList(escopos.split("\\s+")).contains(escopo);
	}

	/** Token para o Drive do proprio usuario; exige que a conexao tenha sido feita com o acesso ao Drive. */
	String tokenDeAcessoDrive(Long usuarioId) {
		var escopos = jdbc.queryForList("select escopos from conexao_google where usuario_id = ?", String.class, usuarioId);
		if (escopos.isEmpty() || !temEscopo(escopos.get(0), ESCOPO_DRIVE)) {
			throw new ConexaoGoogleException("A conta Google nao esta conectada com acesso ao Drive; reconecte autorizando o Drive");
		}
		String t = tokenDeAcesso(usuarioId);
		jdbc.update("update conexao_google set ultimo_uso_em = now() where usuario_id = ?", usuarioId);
		return t;
	}

	String tokenDeAcesso(Long usuarioId) {
		exigirDisponivel();
		AcessoEmCache c = cache.get(usuarioId);
		if (c != null && c.expira().isAfter(relogio.instant())) {
			return c.token();
		}
		var linhas = jdbc.query("select refresh_token_cifrado from conexao_google where usuario_id = ? and estado = 'ATIVA'",
				(rs, n) -> rs.getBytes(1), usuarioId);
		if (linhas.isEmpty()) {
			throw new ConexaoGoogleException("Conta Google nao conectada", true);
		}
		try {
			GoogleApi.Tokens t = api.renovar(clientId, segredo, cifra.decifrar(linhas.get(0), aad(usuarioId)));
			cache.put(usuarioId, new AcessoEmCache(t.acesso(), relogio.instant().plusSeconds(Math.max(60, t.expiraEmSegundos() - 60))));
			return t.acesso();
		} catch (ConexaoGoogleException e) {
			if (e.expirada()) {
				cache.remove(usuarioId);
				jdbc.update("update conexao_google set estado = 'EXPIRADA' where usuario_id = ?", usuarioId);
				auditoria.registrar(usuarioId, "GOOGLE_EXPIRADA", "conexao_google", String.valueOf(usuarioId), null);
			}
			throw e;
		}
	}

	private static String aad(Long usuarioId) {
		return "conexao_google:" + usuarioId;
	}

	private OffsetDateTime agora() {
		return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
	}

	private static String aleatorio(int bytes) {
		byte[] b = new byte[bytes];
		RANDOM.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	private static byte[] digest(String s) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String sha256(String s) {
		return HexFormat.of().formatHex(digest(s));
	}

	private static String sha256Base64Url(String s) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(s));
	}
}
