package br.com.crispimcampos.gestao.documentos;

import br.com.crispimcampos.gestao.agenda.ArmazenamentoAnexos;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.cadastros.ProcessoDto;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.drive.DriveEscritorio;
import br.com.crispimcampos.gestao.google.GoogleDriveService.ArquivoDrive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arquivos que ja estao na pasta do cliente no Drive e ainda nao estao no sistema: sugere o tipo e o processo (pelo numero CNJ no
 * nome ou nas pastas) e, quando a socia confirma, associa por REFERENCIA (nada e copiado nem apagado no Drive).
 */
@Service
public class DocumentoDriveService {

	static final Pattern CNJ = Pattern.compile("(\\d{7})-?(\\d{2})\\.?(\\d{4})\\.?(\\d)\\.?(\\d{2})\\.?(\\d{4})");

	public record Sugestao(String driveId, String nome, String mime, long tamanho, Instant modificadoEm, List<String> caminho, TipoDocumento tipo,
			Long processoId, String processoNumero) {
	}

	public record Sugestoes(boolean driveAtivo, boolean pastaEncontrada, String pastaNome, List<Sugestao> itens) {
	}

	public record Escolha(String driveId, TipoDocumento tipo, Long processoId) {
	}

	private final DriveEscritorio drive;
	private final CadastroService cadastros;
	private final JdbcTemplate jdbc;
	private final AuditoriaService auditoria;

	DocumentoDriveService(DriveEscritorio drive, CadastroService cadastros, JdbcTemplate jdbc, AuditoriaService auditoria) {
		this.drive = drive;
		this.cadastros = cadastros;
		this.jdbc = jdbc;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public Sugestoes sugerir(UsuarioLogado u, Long clienteId) {
		exigirSocia(u);
		var cliente = cadastros.buscarCliente(u, clienteId);
		if (!drive.ativo()) {
			return new Sugestoes(false, false, null, List.of());
		}
		var pasta = drive.pastaDoCliente(cliente.nome());
		if (pasta.isEmpty()) {
			return new Sugestoes(true, false, null, List.of());
		}
		List<ProcessoDto> processos = cadastros.listarProcessos(u, clienteId);
		Set<String> ja = idsJaNoSistema();
		List<Sugestao> itens = drive.arvore(pasta.get().id()).stream().filter(a -> !ja.contains(a.id())).map(a -> sugerir(a, processos)).toList();
		return new Sugestoes(true, true, pasta.get().nome(), itens);
	}

	@Transactional
	public int associar(UsuarioLogado u, Long clienteId, List<Escolha> escolhas) {
		exigirSocia(u);
		var cliente = cadastros.buscarCliente(u, clienteId);
		var pasta = drive.pastaDoCliente(cliente.nome()).orElseThrow(() -> new IllegalArgumentException("Pasta do cliente nao encontrada no Drive"));
		Map<String, ArquivoDrive> naPasta = new java.util.HashMap<>();
		drive.arvore(pasta.id()).forEach(a -> naPasta.put(a.id(), a)); // so arquivos DESTA pasta de cliente podem ser associados
		Set<String> ja = idsJaNoSistema();
		int feitos = 0;
		for (Escolha e : escolhas) {
			ArquivoDrive a = naPasta.get(e.driveId());
			if (a == null || ja.contains(a.id())) {
				continue;
			}
			String caminho = ArmazenamentoAnexos.PREFIXO_DRIVE + a.id();
			if (e.processoId() != null) {
				ProcessoDto p = cadastros.buscarProcesso(u, e.processoId());
				if (!clienteId.equals(p.clienteId())) {
					throw new IllegalArgumentException("Processo de outro cliente");
				}
				jdbc.update("insert into anexo (processo_id, nome_original, tamanho, caminho, autor_id) values (?, ?, ?, ?, ?)",
						p.id(), nomeCurto(a.nome()), a.tamanho(), caminho, u.id());
			} else {
				TipoDocumento tipo = e.tipo() != null ? e.tipo() : TipoDocumento.pelNome(a.nome());
				jdbc.update("insert into documento_cliente (cliente_id, tipo, nome_original, mime, tamanho, caminho, sha256, restrito_socias, origem, drive_id, "
						+ "drive_modificado_em, autor_id) values (?, ?, ?, ?, ?, ?, ?, ?, 'DRIVE', ?, ?, ?)", clienteId, tipo.name(), nomeCurto(a.nome()), a.mime(),
						a.tamanho(), caminho, sha256(caminho), tipo == TipoDocumento.CONTRATO_HONORARIOS, a.id(),
						a.modificadoEm() == null ? null : java.sql.Timestamp.from(a.modificadoEm()), u.id());
			}
			auditoria.registrar(u.id(), "DRIVE_ARQUIVO_ASSOCIADO", "cliente", String.valueOf(clienteId), e.processoId() == null ? "documento" : "anexo-processo");
			ja.add(a.id());
			feitos++;
		}
		return feitos;
	}

	private Sugestao sugerir(ArquivoDrive a, List<ProcessoDto> processos) {
		String texto = String.join(" ", a.caminho()) + " " + a.nome();
		Matcher m = CNJ.matcher(texto);
		ProcessoDto achado = null;
		while (m.find() && achado == null) {
			String digitos = m.group(1) + m.group(2) + m.group(3) + m.group(4) + m.group(5) + m.group(6);
			achado = processos.stream().filter(p -> digitos.equals(p.numero())).findFirst().orElse(null);
		}
		return new Sugestao(a.id(), a.nome(), a.mime(), a.tamanho(), a.modificadoEm(), a.caminho(), TipoDocumento.pelNome(a.nome()),
				achado == null ? null : achado.id(), achado == null ? null : achado.numeroFormatado());
	}

	/** Ids do Drive que ja estao no sistema (documento do cliente ou anexo de processo). */
	private Set<String> idsJaNoSistema() {
		Set<String> ids = new HashSet<>(jdbc.queryForList("select drive_id from documento_cliente where drive_id is not null", String.class));
		jdbc.queryForList("select caminho from anexo where caminho like 'drive:%'", String.class)
				.forEach(c -> ids.add(c.substring(ArmazenamentoAnexos.PREFIXO_DRIVE.length())));
		return ids;
	}

	private static void exigirSocia(UsuarioLogado u) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("Somente as socias associam arquivos do Drive");
		}
	}

	private static String nomeCurto(String n) {
		String s = n == null || n.isBlank() ? "arquivo" : n.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
		return s.length() > 255 ? s.substring(0, 255) : s;
	}

	private static String sha256(String s) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
