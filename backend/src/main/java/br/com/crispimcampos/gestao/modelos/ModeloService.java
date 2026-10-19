package br.com.crispimcampos.gestao.modelos;

import br.com.crispimcampos.gestao.agenda.ArmazenamentoAnexos;
import br.com.crispimcampos.gestao.agenda.TipoArquivo;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Biblioteca de modelos de pecas (item 10), por tipo (INI, CON, MAN, REC) e area do direito. Todos da equipe consultam;
 * so socias cadastram e desativam. Arquivos com a mesma validacao dos anexos (extensao + assinatura, 20 MB).
 */
@Service
public class ModeloService {

	static final Set<String> TIPOS = Set.of("INI", "CON", "MAN", "REC");
	static final long TAMANHO_MAXIMO = 20L * 1024 * 1024;

	private static final RowMapper<ModeloDto> MAPA = (rs, n) -> new ModeloDto(rs.getLong("id"), rs.getString("tipo"),
			rs.getString("area_direito"), rs.getString("titulo"), rs.getString("nome_original"), rs.getLong("tamanho"),
			rs.getBoolean("ativo"), rs.getLong("criado_por"), rs.getTimestamp("criado_em").toInstant());

	public record Download(String nome, Resource arquivo) {
	}

	private final JdbcTemplate jdbc;
	private final ArmazenamentoAnexos armazenamento;
	private final AuditoriaService auditoria;

	ModeloService(JdbcTemplate jdbc, ArmazenamentoAnexos armazenamento, AuditoriaService auditoria) {
		this.jdbc = jdbc;
		this.armazenamento = armazenamento;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<ModeloDto> listar(String tipo, String area) {
		return jdbc.query("select * from modelo_peca where ativo and (?::text is null or tipo = ?) and (?::text is null or lower(area_direito) = lower(?)) "
				+ "order by tipo, area_direito, titulo", MAPA, tipo, tipo, area, area);
	}

	@Transactional(readOnly = true)
	public List<String> areas() {
		return jdbc.queryForList("select distinct area_direito from modelo_peca where ativo order by 1", String.class);
	}

	@Transactional
	public ModeloDto criar(UsuarioLogado ator, String tipo, String area, String titulo, String nomeArquivo, byte[] conteudo) {
		if (ator.perfil() != Perfil.SOCIA) {
			throw new SecurityException("Somente socias cadastram modelos");
		}
		if (!TIPOS.contains(tipo)) {
			throw new IllegalArgumentException("Tipo invalido (INI, CON, MAN ou REC)");
		}
		if (area == null || area.isBlank() || titulo == null || titulo.isBlank()) {
			throw new IllegalArgumentException("Informe a area do direito e o titulo");
		}
		String nome = nomeArquivo == null ? "" : nomeArquivo.replace('\\', '/');
		nome = nome.substring(nome.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
		if (nome.isEmpty() || nome.length() > 255) {
			throw new IllegalArgumentException("Nome de arquivo invalido");
		}
		if (conteudo.length == 0 || conteudo.length > TAMANHO_MAXIMO) {
			throw new IllegalArgumentException("Arquivo vazio ou maior que 20 MB");
		}
		if (!TipoArquivo.aceito(nome, conteudo)) {
			throw new IllegalArgumentException("Tipo de arquivo nao permitido (aceitos: pdf, doc, docx, xls, xlsx, odt, png, jpg)");
		}
		String caminho = armazenamento.salvar(conteudo);
		Long id = jdbc.queryForObject("insert into modelo_peca (tipo, area_direito, titulo, nome_original, tamanho, caminho, criado_por) "
				+ "values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class, tipo, area.trim(), titulo.trim(), nome, conteudo.length, caminho, ator.id());
		auditoria.registrar(ator.id(), "MODELO_CADASTRADO", "modelo_peca", String.valueOf(id), "tipo=" + tipo);
		return jdbc.queryForObject("select * from modelo_peca where id = ?", MAPA, id);
	}

	@Transactional
	public void desativar(UsuarioLogado ator, Long id) {
		if (ator.perfil() != Perfil.SOCIA) {
			throw new SecurityException("Somente socias desativam modelos");
		}
		if (jdbc.update("update modelo_peca set ativo = false where id = ? and ativo", id) == 0) {
			throw new ModeloNaoEncontradoException("Modelo nao encontrado");
		}
		auditoria.registrar(ator.id(), "MODELO_DESATIVADO", "modelo_peca", String.valueOf(id), null);
	}

	@Transactional(readOnly = true)
	public Download baixar(Long id) {
		var l = jdbc.queryForList("select nome_original, caminho from modelo_peca where id = ? and ativo", id);
		if (l.isEmpty()) {
			throw new ModeloNaoEncontradoException("Modelo nao encontrado");
		}
		return new Download((String) l.get(0).get("nome_original"), armazenamento.abrir((String) l.get(0).get("caminho")));
	}
}
