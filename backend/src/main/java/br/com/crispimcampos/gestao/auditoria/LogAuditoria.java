package br.com.crispimcampos.gestao.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "log_auditoria")
class LogAuditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, insertable = false, updatable = false)
	private Instant momento;

	@Column(name = "usuario_id", updatable = false)
	private Long usuarioId;

	@Column(nullable = false, updatable = false)
	private String acao;

	@Column(updatable = false)
	private String entidade;

	@Column(name = "entidade_id", updatable = false)
	private String entidadeId;

	@Column(updatable = false)
	private String detalhe;

	@Column(updatable = false)
	private String ip;

	protected LogAuditoria() {
	}

	LogAuditoria(Long usuarioId, String acao, String entidade, String entidadeId, String detalhe, String ip) {
		this.usuarioId = usuarioId;
		this.acao = acao;
		this.entidade = entidade;
		this.entidadeId = entidadeId;
		this.detalhe = detalhe;
		this.ip = ip;
	}
}
