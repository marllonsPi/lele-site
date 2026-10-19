package br.com.crispimcampos.gestao.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Historico imutavel (trigger no banco): quem escreveu, o que e quando. */
@Entity
@Table(name = "observacao_agendamento")
class ObservacaoAgendamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "agendamento_id", nullable = false, updatable = false)
	private Long agendamentoId;

	@Column(name = "autor_id", nullable = false, updatable = false)
	private Long autorId;

	@Column(nullable = false, updatable = false)
	private String texto;

	@Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
	private Instant criadoEm;

	protected ObservacaoAgendamento() {
	}

	ObservacaoAgendamento(Long agendamentoId, Long autorId, String texto) {
		this.agendamentoId = agendamentoId;
		this.autorId = autorId;
		this.texto = texto;
	}

	Long getId() { return id; }
	Long getAutorId() { return autorId; }
	String getTexto() { return texto; }
	Instant getCriadoEm() { return criadoEm; }
}
