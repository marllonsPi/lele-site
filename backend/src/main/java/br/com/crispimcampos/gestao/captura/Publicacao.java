package br.com.crispimcampos.gestao.captura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Publicacao unica (cruzada entre fontes) na fila da socia. Nunca e apagada: so classificada ou descartada com motivo. */
@Entity
@Table(name = "publicacao")
class Publicacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "numero_processo", nullable = false, updatable = false)
	private String numeroProcesso;

	@Column(name = "titular_id", nullable = false, updatable = false)
	private Long titularId;

	@Column(name = "data_publicacao", nullable = false, updatable = false)
	private LocalDate dataPublicacao;

	private String tribunal;
	private String resumo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoPublicacao estado = EstadoPublicacao.A_CLASSIFICAR;

	@Column(name = "processo_id")
	private Long processoId;

	@Column(name = "agendamento_id")
	private Long agendamentoId;

	@Column(name = "motivo_descarte")
	private String motivoDescarte;

	protected Publicacao() {
	}

	Publicacao(String numeroProcesso, Long titularId, LocalDate data, String tribunal, String resumo, Long processoId) {
		this.numeroProcesso = numeroProcesso;
		this.titularId = titularId;
		this.dataPublicacao = data;
		this.tribunal = tribunal;
		this.resumo = resumo;
		this.processoId = processoId;
	}

	void vincularProcesso(Long processoId) {
		exigirAClassificar();
		this.processoId = processoId;
	}

	void classificar(Long agendamentoId) {
		exigirAClassificar();
		this.agendamentoId = agendamentoId;
		this.estado = EstadoPublicacao.CLASSIFICADA;
	}

	void descartar(String motivo) {
		exigirAClassificar();
		this.motivoDescarte = motivo;
		this.estado = EstadoPublicacao.DESCARTADA_COM_REGISTRO;
	}

	private void exigirAClassificar() {
		if (estado != EstadoPublicacao.A_CLASSIFICAR) {
			throw new IllegalStateException("A publicacao ja foi tratada");
		}
	}

	Long getId() { return id; }
	String getNumeroProcesso() { return numeroProcesso; }
	Long getTitularId() { return titularId; }
	LocalDate getDataPublicacao() { return dataPublicacao; }
	String getTribunal() { return tribunal; }
	String getResumo() { return resumo; }
	EstadoPublicacao getEstado() { return estado; }
	Long getProcessoId() { return processoId; }
	Long getAgendamentoId() { return agendamentoId; }
	String getMotivoDescarte() { return motivoDescarte; }
}
