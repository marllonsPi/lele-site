package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pecas e documentos do agendamento e do processo (item 10). Acesso segue a visibilidade do dono:
 * agendamento (escopo AGENDAMENTO) ou processo (escopo PROCESSO). Sem antivirus ainda (pendencia 29).
 */
@Service
public class AnexoService {

	static final long TAMANHO_MAXIMO = 20L * 1024 * 1024;

	private final AnexoRepository anexos;
	private final ArmazenamentoAnexos armazenamento;
	private final RegrasAgendamento regras;
	private final CadastroService cadastros;
	private final AuditoriaService auditoria;

	AnexoService(AnexoRepository anexos, ArmazenamentoAnexos armazenamento, RegrasAgendamento regras,
			CadastroService cadastros, AuditoriaService auditoria) {
		this.anexos = anexos;
		this.armazenamento = armazenamento;
		this.regras = regras;
		this.cadastros = cadastros;
		this.auditoria = auditoria;
	}

	public record Download(String nome, Resource arquivo) {
	}

	@Transactional(readOnly = true)
	public List<AnexoDto> listarDoAgendamento(UsuarioLogado u, Long agendamentoId) {
		regras.obterVisivel(u, agendamentoId);
		return anexos.findByAgendamentoIdOrderByIdAsc(agendamentoId).stream().map(AnexoDto::de).toList();
	}

	@Transactional(readOnly = true)
	public List<AnexoDto> listarDoProcesso(UsuarioLogado u, Long processoId) {
		cadastros.buscarProcesso(u, processoId);
		return anexos.findByProcessoIdOrderByIdAsc(processoId).stream().map(AnexoDto::de).toList();
	}

	@Transactional
	public AnexoDto anexarAoAgendamento(UsuarioLogado u, Long agendamentoId, String nome, byte[] conteudo) {
		regras.obterVisivel(u, agendamentoId);
		return gravar(u, null, agendamentoId, nome, conteudo);
	}

	@Transactional
	public AnexoDto anexarAoProcesso(UsuarioLogado u, Long processoId, String nome, byte[] conteudo) {
		cadastros.buscarProcesso(u, processoId);
		return gravar(u, processoId, null, nome, conteudo);
	}

	@Transactional // grava auditoria: nao pode ser readOnly
	public Download baixar(UsuarioLogado u, Long anexoId) {
		Anexo a = anexos.findById(anexoId).orElseThrow(() -> new AgendaNaoEncontradaException("Anexo nao encontrado"));
		if (a.getAgendamentoId() != null) {
			regras.obterVisivel(u, a.getAgendamentoId());
		} else {
			cadastros.buscarProcesso(u, a.getProcessoId());
		}
		auditoria.registrar(u.id(), "ANEXO_BAIXADO", "anexo", String.valueOf(anexoId), null);
		return new Download(a.getNomeOriginal(), armazenamento.abrir(a.getCaminho()));
	}

	private AnexoDto gravar(UsuarioLogado u, Long processoId, Long agendamentoId, String nomeEnviado, byte[] conteudo) {
		String nome = nomeLimpo(nomeEnviado);
		if (conteudo.length == 0) {
			throw RegrasAgendamento.regra("Arquivo vazio");
		}
		if (conteudo.length > TAMANHO_MAXIMO) {
			throw RegrasAgendamento.regra("Arquivo maior que 20 MB");
		}
		if (!TipoArquivo.aceito(nome, conteudo)) {
			throw RegrasAgendamento.regra("Tipo de arquivo nao permitido (aceitos: pdf, doc, docx, xls, xlsx, odt, png, jpg)");
		}
		String caminho = armazenamento.salvar(conteudo);
		Anexo a = anexos.save(new Anexo(processoId, agendamentoId, nome, conteudo.length, caminho, u.id()));
		auditoria.registrar(u.id(), "ANEXO_ADICIONADO", "anexo", String.valueOf(a.getId()), null);
		return AnexoDto.de(a);
	}

	/** Remove diretorios e caracteres de controle do nome enviado. */
	static String nomeLimpo(String enviado) {
		String n = enviado == null ? "" : enviado.replace('\\', '/');
		n = n.substring(n.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
		if (n.isEmpty() || n.length() > 255) {
			throw RegrasAgendamento.regra("Nome de arquivo invalido");
		}
		return n;
	}
}
