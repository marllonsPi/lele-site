package br.com.crispimcampos.gestao.documentos;

import br.com.crispimcampos.gestao.agenda.ArmazenamentoAnexos;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.drive.DriveEscritorio;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Leva para o Drive os anexos que ficaram no disco local (de antes do Drive ou de falhas). Em lotes pequenos, repetivel. O arquivo local
 * NAO e apagado (fica como copia de seguranca; a referencia passa a apontar para o Drive).
 */
@Service
public class DriveMigracaoService {

	private static final Logger log = LoggerFactory.getLogger(DriveMigracaoService.class);
	static final int LOTE = 15;

	private final JdbcTemplate jdbc;
	private final ArmazenamentoAnexos armazenamento;
	private final DriveEscritorio drive;
	private final AuditoriaService auditoria;

	DriveMigracaoService(JdbcTemplate jdbc, ArmazenamentoAnexos armazenamento, DriveEscritorio drive, AuditoriaService auditoria) {
		this.jdbc = jdbc;
		this.armazenamento = armazenamento;
		this.drive = drive;
		this.auditoria = auditoria;
	}

	public int pendentes() {
		Integer a = jdbc.queryForObject("select count(*) from documento_cliente where caminho not like 'drive:%'", Integer.class);
		Integer b = jdbc.queryForObject("select count(*) from anexo where caminho not like 'drive:%'", Integer.class);
		return (a == null ? 0 : a) + (b == null ? 0 : b);
	}

	/** Move um lote; devolve {movidos, falhas, restantes}. */
	public Map<String, Integer> moverLote(Long usuarioId) {
		if (!drive.ativo()) {
			throw new IllegalStateException("Drive do escritorio nao esta ativo");
		}
		int movidos = 0;
		int falhas = 0;
		for (var d : jdbc.queryForList("select d.id, d.nome_original, d.mime, d.caminho, d.drive_id, c.nome as cliente from documento_cliente d "
				+ "join cliente c on c.id = d.cliente_id where d.caminho not like 'drive:%' order by d.id limit ?", LOTE)) {
			try {
				String caminho = String.valueOf(d.get("caminho"));
				String id = drive.enviar(DriveEscritorio.caminho(String.valueOf(d.get("cliente"))), String.valueOf(d.get("nome_original")),
						d.get("mime") == null ? null : String.valueOf(d.get("mime")), armazenamento.abrir(caminho).getContentAsByteArray());
				boolean temDriveId = d.get("drive_id") != null;
				jdbc.update("update documento_cliente set caminho = ?, drive_id = coalesce(drive_id, ?) where id = ?", "drive:" + id, temDriveId ? null : id, d.get("id"));
				movidos++;
			} catch (Exception e) {
				falhas++;
				log.warn("Documento {} nao foi para o Drive: {}", d.get("id"), e.getClass().getSimpleName());
			}
		}
		for (var a : jdbc.queryForList("select a.id, a.nome_original, a.caminho, a.processo_id, p.numero, c.nome as cliente from anexo a "
				+ "left join processo p on p.id = a.processo_id left join cliente c on c.id = p.cliente_id where a.caminho not like 'drive:%' order by a.id limit ?", LOTE)) {
			try {
				String numero = a.get("numero") == null ? null : formatar(String.valueOf(a.get("numero")));
				List<String> pastas = a.get("cliente") == null ? DriveEscritorio.caminho("_Agenda") : DriveEscritorio.caminho(String.valueOf(a.get("cliente")), numero);
				String id = drive.enviar(pastas, String.valueOf(a.get("nome_original")), null, armazenamento.abrir(String.valueOf(a.get("caminho"))).getContentAsByteArray());
				jdbc.update("update anexo set caminho = ? where id = ?", "drive:" + id, a.get("id"));
				movidos++;
			} catch (Exception e) {
				falhas++;
				log.warn("Anexo {} nao foi para o Drive: {}", a.get("id"), e.getClass().getSimpleName());
			}
		}
		auditoria.registrar(usuarioId, "DRIVE_ANEXOS_MOVIDOS", "drive", null, "movidos=" + movidos + " falhas=" + falhas);
		return Map.of("movidos", movidos, "falhas", falhas, "restantes", pendentes());
	}

	/** CNJ com mascara (0000000-00.0000.0.00.0000) a partir dos 20 digitos. */
	static String formatar(String n) {
		return n.length() == 20 ? n.substring(0, 7) + "-" + n.substring(7, 9) + "." + n.substring(9, 13) + "." + n.charAt(13) + "." + n.substring(14, 16) + "." + n.substring(16) : n;
	}
}
