package br.com.crispimcampos.gestao.comentarios;

import br.com.crispimcampos.gestao.alertas.EnviadorEmail;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comentarios do site: o visitante envia, as socias recebem por e-mail (e na fila do sistema) e a primeira decisao
 * vale. Aprovado aparece no site na hora; nada e publicado sem decisao humana. O token do e-mail so existe em claro
 * no e-mail; no banco fica o SHA-256.
 */
@Service
@EnableScheduling
public class ComentarioService {

	private static final Logger log = LoggerFactory.getLogger(ComentarioService.class);
	static final int MAX_PENDENTES = 200;
	private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>\"]+@[^\\s@<>\"]+\\.[^\\s@<>\"]{2,}$");
	private static final SecureRandom ALEATORIO = new SecureRandom();

	public record Publico(String nome, String mensagem, Instant data) {
	}

	public record Fila(Long id, String nome, String email, String mensagem, String estado, Instant criadoEm, Instant decididoEm,
			String decisaoVia, boolean avisoEnviado) {
	}

	public record Resumo(Long id, String nome, String mensagem, String email) {
	}

	/** Entrada invalida (o controller devolve 422 com a mensagem). */
	public static class ComentarioInvalidoException extends RuntimeException {
		ComentarioInvalidoException(String m) {
			super(m);
		}
	}

	private final JdbcTemplate jdbc;
	private final UsuarioService usuarios;
	private final AuditoriaService auditoria;
	private final ObjectProvider<EnviadorEmail> email;
	private final String urlSite;

	ComentarioService(JdbcTemplate jdbc, UsuarioService usuarios, AuditoriaService auditoria, ObjectProvider<EnviadorEmail> email,
			@Value("${app.site.url:https://www.crispimcamposadvogados.com.br}") String urlSite) {
		this.jdbc = jdbc;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.email = email;
		this.urlSite = urlSite.replaceAll("/+$", "");
	}

	/** Recebe do site. Valida, guarda como PENDENTE e avisa as socias (se o e-mail falhar, a fila do sistema ainda mostra). */
	@Transactional
	public void receber(String nome, String emailVisitante, String mensagem) {
		String n = limpar(nome, 80);
		String e = limpar(emailVisitante, 254).toLowerCase();
		String m = limpar(mensagem, 700);
		if (n.length() < 2) {
			throw new ComentarioInvalidoException("Informe seu nome.");
		}
		if (!EMAIL.matcher(e).matches()) {
			throw new ComentarioInvalidoException("Informe um e-mail válido.");
		}
		if (m.length() < 10) {
			throw new ComentarioInvalidoException("Escreva um comentário com pelo menos 10 caracteres.");
		}
		Integer pendentes = jdbc.queryForObject("select count(*) from comentario_site where estado = 'PENDENTE'", Integer.class);
		if (pendentes != null && pendentes >= MAX_PENDENTES) {
			throw new ComentarioInvalidoException("No momento não estamos recebendo novos comentários. Tente mais tarde.");
		}
		String token = novoToken();
		Long id = jdbc.queryForObject(
				"insert into comentario_site (nome, email, mensagem, token_hash) values (?, ?, ?, ?) returning id", Long.class,
				n, e, m, hash(token));
		auditoria.registrar(null, "COMENTARIO_RECEBIDO", "comentario_site", String.valueOf(id), null);
		avisar(id, n, e, m, token);
	}

	/** Aprovados, do mais recente ao mais antigo; sem e-mail. */
	@Transactional(readOnly = true)
	public List<Publico> aprovados() {
		return jdbc.query("select nome, mensagem, coalesce(decidido_em, criado_em) as quando from comentario_site "
				+ "where estado = 'APROVADO' order by coalesce(decidido_em, criado_em) desc, id desc limit 60",
				(rs, i) -> new Publico(rs.getString("nome"), rs.getString("mensagem"), rs.getTimestamp("quando").toInstant()));
	}

	@Transactional(readOnly = true)
	public List<Fila> fila(String estado) {
		String filtro = estado == null || estado.isBlank() ? null : estado.toUpperCase();
		return jdbc.query("select id, nome, email, mensagem, estado, criado_em, decidido_em, decisao_via, aviso_enviado_em "
				+ "from comentario_site where (?::text is null or estado = ?) order by (estado = 'PENDENTE') desc, criado_em desc limit 300",
				(rs, i) -> new Fila(rs.getLong("id"), rs.getString("nome"), rs.getString("email"), rs.getString("mensagem"),
						rs.getString("estado"), rs.getTimestamp("criado_em").toInstant(),
						rs.getTimestamp("decidido_em") == null ? null : rs.getTimestamp("decidido_em").toInstant(),
						rs.getString("decisao_via"), rs.getTimestamp("aviso_enviado_em") != null),
				filtro, filtro);
	}

	/** Dados do comentario do link do e-mail (nada de decisao aqui: abrir o link nao aprova). */
	@Transactional(readOnly = true)
	public Optional<Fila> porToken(String token) {
		if (token == null || token.length() < 20) {
			return Optional.empty();
		}
		List<Fila> l = jdbc.query("select id, nome, email, mensagem, estado, criado_em, decidido_em, decisao_via, aviso_enviado_em "
				+ "from comentario_site where token_hash = ?",
				(rs, i) -> new Fila(rs.getLong("id"), rs.getString("nome"), rs.getString("email"), rs.getString("mensagem"),
						rs.getString("estado"), rs.getTimestamp("criado_em").toInstant(), null, rs.getString("decisao_via"),
						rs.getTimestamp("aviso_enviado_em") != null),
				hash(token));
		return l.stream().findFirst();
	}

	/** Decisao pelo link do e-mail. A primeira decisao vale; devolve o estado final (que pode ser de outra decisao anterior). */
	@Transactional
	public Optional<String> decidirPorToken(String token, boolean aprovar) {
		return porToken(token).map(c -> decidir(c.id(), aprovar, null, "EMAIL"));
	}

	/** Decisao na fila do sistema, por uma socia. Permite reverter (aprovado -> oculto e vice-versa). */
	@Transactional
	public String decidirNoSistema(Long usuarioId, Long id, boolean aprovar) {
		Integer existe = jdbc.queryForObject("select count(*) from comentario_site where id = ?", Integer.class, id);
		if (existe == null || existe == 0) {
			throw new ComentarioInvalidoException("Comentário não encontrado.");
		}
		jdbc.update("update comentario_site set estado = ?, decidido_em = now(), decidido_por = ?, decisao_via = 'SISTEMA' where id = ?",
				aprovar ? "APROVADO" : "REJEITADO", usuarioId, id);
		auditoria.registrar(usuarioId, aprovar ? "COMENTARIO_APROVADO" : "COMENTARIO_REJEITADO", "comentario_site", String.valueOf(id), "SISTEMA");
		return aprovar ? "APROVADO" : "REJEITADO";
	}

	private String decidir(Long id, boolean aprovar, Long usuarioId, String via) {
		String novo = aprovar ? "APROVADO" : "REJEITADO";
		int linhas = jdbc.update("update comentario_site set estado = ?, decidido_em = now(), decidido_por = ?, decisao_via = ? "
				+ "where id = ? and estado = 'PENDENTE'", novo, usuarioId, via, id);
		if (linhas == 1) {
			auditoria.registrar(usuarioId, aprovar ? "COMENTARIO_APROVADO" : "COMENTARIO_REJEITADO", "comentario_site", String.valueOf(id), via);
			return novo;
		}
		return jdbc.queryForObject("select estado from comentario_site where id = ?", String.class, id);
	}

	/** Reenvia a quem ficou sem aviso (SMTP ausente ou falha); roda a cada 10 minutos. */
	@Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
	void reenviarAvisosPendentes() {
		if (email.getIfAvailable() == null) {
			return;
		}
		// o token em claro nao e guardado: gera-se um novo token para o aviso atrasado
		for (Resumo r : jdbc.query("select id, nome, mensagem, email from comentario_site where estado = 'PENDENTE' and aviso_enviado_em is null "
				+ "order by id limit 20", (rs, i) -> new Resumo(rs.getLong("id"), rs.getString("nome"), rs.getString("mensagem"), rs.getString("email")))) {
			String token = novoToken();
			jdbc.update("update comentario_site set token_hash = ? where id = ?", hash(token), r.id());
			avisar(r.id(), r.nome(), r.email(), r.mensagem(), token);
		}
	}

	private void avisar(Long id, String nome, String emailVisitante, String mensagem, String token) {
		EnviadorEmail envio = email.getIfAvailable();
		if (envio == null) {
			log.warn("Comentario {} aguardando moderacao na fila do sistema: SMTP nao configurado, nenhum e-mail enviado", id);
			return;
		}
		List<String> destinos = usuarios.listar().stream()
				.filter(u -> u.ativo() && u.perfil() == Perfil.SOCIA).map(UsuarioDto::destinoAlertas)
				.filter(d -> d != null && !d.isBlank()).distinct().toList();
		if (destinos.isEmpty()) {
			log.warn("Comentario {}: nenhuma socia com e-mail de alertas; ele fica na fila do sistema", id);
			return;
		}
		String link = urlSite + "/api/public/comentarios/moderar?t=" + token;
		String corpo = "Novo comentário recebido pelo site:\n\nNome: " + nome + "\nE-mail: " + emailVisitante + "\n\n" + mensagem
				+ "\n\nPara APROVAR ou REJEITAR, abra o link abaixo e confirme (qualquer uma das sócias pode decidir; a primeira decisão vale).\n"
				+ "Aprovado, o comentário aparece no site na hora.\n\n" + link
				+ "\n\nOu decida em Comentários do site, dentro do sistema.";
		boolean algum = false;
		for (String d : destinos) {
			try {
				envio.enviar(d, "Novo comentário para aprovação", corpo);
				algum = true;
			} catch (RuntimeException e) {
				log.warn("Falha ao avisar socia do comentario {}: {}", id, e.getClass().getSimpleName());
			}
		}
		if (algum) {
			jdbc.update("update comentario_site set aviso_enviado_em = now() where id = ?", id);
		}
	}

	private static String limpar(String v, int max) {
		String s = v == null ? "" : v.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replaceAll("[ \\t]+", " ").trim();
		return s.length() > max ? s.substring(0, max) : s;
	}

	private static String novoToken() {
		byte[] b = new byte[32];
		ALEATORIO.nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
