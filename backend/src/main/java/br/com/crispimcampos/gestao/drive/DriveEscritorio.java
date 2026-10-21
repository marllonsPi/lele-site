package br.com.crispimcampos.gestao.drive;

import br.com.crispimcampos.gestao.google.GoogleDriveService;
import br.com.crispimcampos.gestao.google.GoogleDriveService.ArquivoDrive;
import br.com.crispimcampos.gestao.google.GoogleDriveService.PastaDrive;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * O Drive do escritorio como armazenamento do sistema: uma conta Google (conectada com acesso ao Drive) e uma pasta raiz com uma
 * subpasta por cliente. Anexos novos vao para {@code <raiz>/<cliente>/<processo>}; nada e apagado no Drive. A conta e a pasta ficam no
 * banco (tela Drive do escritorio); o ambiente so fornece o valor inicial.
 */
@Service
public class DriveEscritorio {

	private static final Logger log = LoggerFactory.getLogger(DriveEscritorio.class);
	private static final Pattern ID_NA_URL = Pattern.compile("(?:folders/|[?&]id=)([A-Za-z0-9_-]{10,})");
	private static final Pattern SO_ID = Pattern.compile("[A-Za-z0-9_-]{10,}");

	public record Config(Long contaUsuarioId, String pastaRaizId) {
	}

	private final JdbcTemplate jdbc;
	private final GoogleDriveService drive;
	private final String pastaPadrao;
	private final long contaPadrao;

	DriveEscritorio(JdbcTemplate jdbc, GoogleDriveService drive, @Value("${app.importacao.pasta-clientes-id:}") String pastaPadrao,
			@Value("${app.importacao.conta-usuario-id:0}") long contaPadrao) {
		this.jdbc = jdbc;
		this.drive = drive;
		this.pastaPadrao = pastaPadrao == null ? "" : pastaPadrao.trim();
		this.contaPadrao = contaPadrao;
	}

	public Config config() {
		var l = jdbc.queryForList("select conta_usuario_id, pasta_raiz_id from drive_config where id = 1");
		Long conta = l.isEmpty() || l.get(0).get("conta_usuario_id") == null ? null : ((Number) l.get(0).get("conta_usuario_id")).longValue();
		String pasta = l.isEmpty() || l.get(0).get("pasta_raiz_id") == null ? "" : String.valueOf(l.get(0).get("pasta_raiz_id"));
		if (conta == null && contaPadrao > 0) {
			conta = contaPadrao;
		}
		return new Config(conta, pasta.isBlank() ? pastaPadrao : pasta);
	}

	/** Pronto para uso: conta e pasta definidas e a conta conectada ao Google com acesso ao Drive. */
	public boolean ativo() {
		Config c = config();
		if (c.contaUsuarioId() == null || c.pastaRaizId().isBlank()) {
			return false;
		}
		try {
			return drive.disponivel(c.contaUsuarioId());
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Define a conta (quem chama) e a pasta raiz (id ou link do Drive; vazio mantem a atual). */
	public void definir(Long contaUsuarioId, String pastaIdOuLink) {
		String id = pastaIdOuLink == null || pastaIdOuLink.isBlank() ? config().pastaRaizId() : extrairId(pastaIdOuLink);
		jdbc.update("update drive_config set conta_usuario_id = ?, pasta_raiz_id = ?, atualizado_em = now() where id = 1", contaUsuarioId, id);
	}

	static String extrairId(String texto) {
		String t = texto.trim();
		Matcher m = ID_NA_URL.matcher(t);
		if (m.find()) {
			return m.group(1);
		}
		if (SO_ID.matcher(t).matches()) {
			return t;
		}
		throw new IllegalArgumentException("Informe o link da pasta do Drive ou o ID dela");
	}

	/** Grava o arquivo em {@code <raiz>/<pastas...>} e devolve o id no Drive. */
	public String enviar(List<String> pastas, String nome, String mime, byte[] conteudo) {
		Config c = config();
		if (!ativo()) {
			throw new IllegalStateException("Drive do escritorio nao esta ativo");
		}
		for (int tentativa = 0; ; tentativa++) {
			try {
				return drive.enviar(c.contaUsuarioId(), pastaDe(c, pastas), nome, mime, conteudo);
			} catch (RuntimeException e) {
				if (tentativa == 0) {
					// pasta em cache pode ter sido removida no Drive: esquece o cache e tenta de novo uma vez
					jdbc.update("delete from drive_pasta where chave like ?", chaveBase(c) + "%");
					log.info("Reenviando ao Drive apos limpar o cache de pastas: {}", e.getClass().getSimpleName());
					continue;
				}
				throw e;
			}
		}
	}

	public byte[] baixar(String arquivoId) {
		Config c = config();
		if (c.contaUsuarioId() == null) {
			throw new IllegalStateException("Drive do escritorio nao esta configurado");
		}
		return drive.baixarPorId(c.contaUsuarioId(), arquivoId);
	}

	/** Pasta do cliente sob a raiz: nome igual (sem acento/caixa) ou, na falta, um que contenha o outro (6+ letras). */
	public Optional<PastaDrive> pastaDoCliente(String nomeCliente) {
		Config c = config();
		if (!ativo()) {
			return Optional.empty();
		}
		String alvo = chave(nomeCliente);
		List<PastaDrive> pastas = drive.subpastas(c.contaUsuarioId(), c.pastaRaizId());
		for (PastaDrive p : pastas) {
			if (chave(p.nome()).equals(alvo)) {
				return Optional.of(p);
			}
		}
		if (alvo.length() >= 6) {
			for (PastaDrive p : pastas) {
				String k = chave(p.nome());
				if (k.length() >= 6 && (k.contains(alvo) || alvo.contains(k))) {
					return Optional.of(p);
				}
			}
		}
		return Optional.empty();
	}

	/** Todos os arquivos sob uma pasta (limites de profundidade e quantidade no servico do Drive). */
	public List<ArquivoDrive> arvore(String pastaId) {
		return drive.listarArvore(config().contaUsuarioId(), pastaId);
	}

	private String pastaDe(Config c, List<String> pastas) {
		String atual = c.pastaRaizId();
		StringBuilder k = new StringBuilder(chaveBase(c));
		for (String nome : pastas) {
			String limpo = nomePasta(nome);
			k.append('/').append(chave(limpo));
			String chave = k.toString().length() > 400 ? k.substring(0, 400) : k.toString();
			var em = jdbc.queryForList("select drive_id from drive_pasta where chave = ?", String.class, chave);
			if (!em.isEmpty()) {
				atual = em.get(0);
				continue;
			}
			atual = drive.garantirPasta(c.contaUsuarioId(), atual, limpo);
			jdbc.update("insert into drive_pasta (chave, drive_id) values (?, ?) on conflict (chave) do update set drive_id = excluded.drive_id", chave, atual);
		}
		return atual;
	}

	private static String chaveBase(Config c) {
		return c.pastaRaizId();
	}

	static String nomePasta(String nome) {
		String n = nome == null ? "" : nome.replaceAll("[\\\\/\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
		if (n.isEmpty()) {
			return "Sem nome";
		}
		return n.length() > 120 ? n.substring(0, 120).trim() : n;
	}

	static String chave(String s) {
		return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase().replaceAll("\\s+", " ").trim();
	}

	/** Nomes em sequencia para montar o caminho (ignora vazios). */
	public static List<String> caminho(String... nomes) {
		List<String> l = new ArrayList<>();
		for (String n : nomes) {
			if (n != null && !n.isBlank()) {
				l.add(n);
			}
		}
		return l;
	}
}
