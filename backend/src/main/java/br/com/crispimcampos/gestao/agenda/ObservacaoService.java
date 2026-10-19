package br.com.crispimcampos.gestao.agenda;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observacoes internas de cada agendamento (item 10). Ficam no historico do agendamento e nunca entram no
 * relatorio ao cliente (invariante 6).
 */
@Service
public class ObservacaoService {

	static final int TEXTO_MAXIMO = 5000;

	private final ObservacaoRepository observacoes;
	private final RegrasAgendamento regras;
	private final AuditoriaService auditoria;

	ObservacaoService(ObservacaoRepository observacoes, RegrasAgendamento regras, AuditoriaService auditoria) {
		this.observacoes = observacoes;
		this.regras = regras;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<ObservacaoDto> listar(UsuarioLogado usuario, Long agendamentoId) {
		regras.obterVisivel(usuario, agendamentoId);
		return observacoes.findByAgendamentoIdOrderByIdAsc(agendamentoId).stream().map(ObservacaoDto::de).toList();
	}

	@Transactional
	public ObservacaoDto adicionar(UsuarioLogado usuario, Long agendamentoId, String texto) {
		regras.obterVisivel(usuario, agendamentoId);
		if (texto == null || texto.isBlank()) {
			throw RegrasAgendamento.regra("A observacao nao pode ser vazia");
		}
		if (texto.length() > TEXTO_MAXIMO) {
			throw RegrasAgendamento.regra("Observacao longa demais");
		}
		ObservacaoAgendamento o = observacoes.save(new ObservacaoAgendamento(agendamentoId, usuario.id(), texto.trim()));
		auditoria.registrar(usuario.id(), "OBSERVACAO_ADICIONADA", "agendamento", String.valueOf(agendamentoId), null);
		return ObservacaoDto.de(o);
	}
}
