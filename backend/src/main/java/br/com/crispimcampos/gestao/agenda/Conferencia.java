package br.com.crispimcampos.gestao.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Registro imutavel de cada conferencia (supervisor ou socia): quem, resultado, motivo e quando. */
@Entity
@Table(name = "conferencia")
class Conferencia {

	enum Etapa { SUPERVISOR, SOCIA }

	enum Resultado { CONFERIDO, DEVOLVIDO }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "agendamento_id", nullable = false, updatable = false)
	private Long agendamentoId;

	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Etapa etapa;

	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Resultado resultado;

	@Column(updatable = false)
	private String motivo;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
	private Instant criadoEm;

	protected Conferencia() {
	}

	Conferencia(Long agendamentoId, Etapa etapa, Resultado resultado, String motivo, Long usuarioId) {
		this.agendamentoId = agendamentoId;
		this.etapa = etapa;
		this.resultado = resultado;
		this.motivo = motivo;
		this.usuarioId = usuarioId;
	}

	Long getId() { return id; }
	Etapa getEtapa() { return etapa; }
	Resultado getResultado() { return resultado; }
	String getMotivo() { return motivo; }
	Long getUsuarioId() { return usuarioId; }
	Instant getCriadoEm() { return criadoEm; }
}
