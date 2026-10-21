package br.com.crispimcampos.gestao.documentos;

import br.com.crispimcampos.gestao.agenda.ArmazenamentoAnexos;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.drive.DriveEscritorio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasta do cliente no sistema. Quem ve o cliente ve os documentos, exceto os restritos as socias (contratos de honorarios, que
 * trazem valores). Nunca devolve o caminho no disco; o download e sempre binario.
 */
@Service
public class DocumentoClienteService {

	static final long TAMANHO_MAXIMO = 20L * 1024 * 1024;

	public record Download(String nome, Resource arquivo) {
	}

	private final JdbcTemplate jdbc;
	private final ArmazenamentoAnexos armazenamento;
	private final CadastroService cadastros;
	private final AuditoriaService auditoria;

	DocumentoClienteService(JdbcTemplate jdbc, ArmazenamentoAnexos armazenamento, CadastroService cadastros, AuditoriaService auditoria) {
		this.jdbc = jdbc;
		this.armazenamento = armazenamento;
		this.cadastros = cadastros;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<DocumentoClienteDto> listar(UsuarioLogado u, Long clienteId) {
		cadastros.buscarCliente(u, clienteId); // sem visibilidade, responde como inexistente
		boolean socia = u.perfil() == Perfil.SOCIA;
		return jdbc.query("select id, cliente_id, tipo, nome_original, tamanho, restrito_socias, origem, criado_em from documento_cliente where cliente_id = ? "
				+ (socia ? "" : "and not restrito_socias ") + "order by id", (rs, n) -> new DocumentoClienteDto(rs.getLong("id"), rs.getLong("cliente_id"),
				TipoDocumento.valueOf(rs.getString("tipo")), rs.getString("nome_original"), rs.getLong("tamanho"), rs.getBoolean("restrito_socias"),
				rs.getString("origem"), rs.getTimestamp("criado_em").toInstant()), clienteId);
	}

	@Transactional
	public Download baixar(UsuarioLogado u, Long id) {
		var linhas = jdbc.queryForList("select cliente_id, nome_original, caminho, restrito_socias from documento_cliente where id = ?", id);
		if (linhas.isEmpty()) {
			throw new IllegalArgumentException("Documento nao encontrado");
		}
		var d = linhas.get(0);
		Long clienteId = ((Number) d.get("cliente_id")).longValue();
		boolean restrito = Boolean.TRUE.equals(d.get("restrito_socias"));
		try {
			cadastros.buscarCliente(u, clienteId);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Documento nao encontrado");
		}
		if (restrito && u.perfil() != Perfil.SOCIA) {
			throw new IllegalArgumentException("Documento nao encontrado");
		}
		auditoria.registrar(u.id(), "DOCUMENTO_CLIENTE_BAIXADO", "documento_cliente", String.valueOf(id), null);
		return new Download(String.valueOf(d.get("nome_original")), armazenamento.abrir(String.valueOf(d.get("caminho"))));
	}

	/** Envio manual de documento (socias). */
	@Transactional
	public DocumentoClienteDto enviar(UsuarioLogado u, Long clienteId, TipoDocumento tipo, String nome, byte[] conteudo) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("Somente as socias anexam documentos ao cliente");
		}
		cadastros.buscarCliente(u, clienteId);
		Long id = anexar(u.id(), clienteId, tipo, nome, null, conteudo, "MANUAL", null, null);
		return listar(u, clienteId).stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
	}

	/** Grava o arquivo e o registro. Uso interno e da importacao; o chamador ja checou a permissao. */
	@Transactional
	public Long anexar(Long autorId, Long clienteId, TipoDocumento tipo, String nome, String mime, byte[] conteudo, String origem, String driveId,
			Instant driveModificadoEm) {
		if (conteudo == null || conteudo.length == 0) {
			throw new IllegalArgumentException("Arquivo vazio");
		}
		if (conteudo.length > TAMANHO_MAXIMO) {
			throw new IllegalArgumentException("Arquivo maior que 20 MB");
		}
		// Arquivo que ja esta no Drive (importacao) so ganha uma referencia; novo vai para o Drive do escritorio (ou disco, se inativo)
		String caminho;
		String idDrive = driveId;
		if (driveId != null) {
			caminho = ArmazenamentoAnexos.PREFIXO_DRIVE + driveId;
		} else {
			caminho = armazenamento.salvarPreferindoDrive(DriveEscritorio.caminho(nomeDoCliente(clienteId)), nomeLimpo(nome), mime, conteudo);
			if (ArmazenamentoAnexos.noDrive(caminho)) {
				idDrive = caminho.substring(ArmazenamentoAnexos.PREFIXO_DRIVE.length());
			}
		}
		return jdbc.queryForObject("insert into documento_cliente (cliente_id, tipo, nome_original, mime, tamanho, caminho, sha256, restrito_socias, origem, drive_id, "
				+ "drive_modificado_em, autor_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id", Long.class, clienteId, tipo.name(), nomeLimpo(nome), mime,
				(long) conteudo.length, caminho, sha256(conteudo), tipo == TipoDocumento.CONTRATO_HONORARIOS, ArmazenamentoAnexos.noDrive(caminho) ? "DRIVE" : origem, idDrive,
				driveModificadoEm == null ? null : java.sql.Timestamp.from(driveModificadoEm), autorId);
	}

	private String nomeDoCliente(Long clienteId) {
		var l = jdbc.queryForList("select nome from cliente where id = ?", String.class, clienteId);
		return l.isEmpty() ? null : l.get(0);
	}

	/** Documento ja importado do Drive (idempotencia): id do registro, se houver. */
	@Transactional(readOnly = true)
	public Optional<Long> idPorDrive(String driveId) {
		return jdbc.queryForList("select id from documento_cliente where drive_id = ?", Long.class, driveId).stream().findFirst();
	}

	static String nomeLimpo(String nome) {
		String n = nome == null ? "" : nome.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
		if (n.isEmpty()) {
			n = "documento";
		}
		return n.length() > 255 ? n.substring(0, 255) : n;
	}

	private static String sha256(byte[] b) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static byte[] utf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}
}
