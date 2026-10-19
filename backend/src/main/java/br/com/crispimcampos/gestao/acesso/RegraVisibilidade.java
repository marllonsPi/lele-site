package br.com.crispimcampos.gestao.acesso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** O observador enxerga o que esta em nome do titular, naquele escopo. */
@Entity
@Table(name = "regra_visibilidade")
class RegraVisibilidade {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "observador_id", nullable = false, updatable = false)
	private Long observadorId;

	@Column(name = "titular_id", nullable = false, updatable = false)
	private Long titularId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Escopo escopo;

	protected RegraVisibilidade() {
	}

	RegraVisibilidade(Long observadorId, Long titularId, Escopo escopo) {
		this.observadorId = observadorId;
		this.titularId = titularId;
		this.escopo = escopo;
	}

	Long getObservadorId() {
		return observadorId;
	}

	Long getTitularId() {
		return titularId;
	}

	Escopo getEscopo() {
		return escopo;
	}
}
