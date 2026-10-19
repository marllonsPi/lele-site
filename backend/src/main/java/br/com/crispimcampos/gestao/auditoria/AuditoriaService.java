package br.com.crispimcampos.gestao.auditoria;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de acoes (quem, o que, quando). Nunca receber dado pessoal em `detalhe`
 * alem do estritamente necessario.
 */
@Service
public class AuditoriaService {

	private final LogAuditoriaRepository repositorio;

	AuditoriaService(LogAuditoriaRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void registrar(Long usuarioId, String acao, String entidade, String entidadeId, String detalhe) {
		registrar(usuarioId, acao, entidade, entidadeId, detalhe, null);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public void registrar(Long usuarioId, String acao, String entidade, String entidadeId, String detalhe, String ip) {
		repositorio.save(new LogAuditoria(usuarioId, acao, entidade, entidadeId, detalhe, ip));
	}
}
