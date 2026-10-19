package br.com.crispimcampos.gestao.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "anexo")
class Anexo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "processo_id", updatable = false)
	private Long processoId;

	@Column(name = "agendamento_id", updatable = false)
	private Long agendamentoId;

	@Column(name = "nome_original", nullable = false, updatable = false)
	private String nomeOriginal;

	@Column(nullable = false, updatable = false)
	private long tamanho;

	@Column(nullable = false, updatable = false)
	private String caminho;

	@Column(name = "autor_id", nullable = false, updatable = false)
	private Long autorId;

	@Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
	private Instant criadoEm;

	protected Anexo() {
	}

	Anexo(Long processoId, Long agendamentoId, String nomeOriginal, long tamanho, String caminho, Long autorId) {
		this.processoId = processoId;
		this.agendamentoId = agendamentoId;
		this.nomeOriginal = nomeOriginal;
		this.tamanho = tamanho;
		this.caminho = caminho;
		this.autorId = autorId;
	}

	Long getId() { return id; }
	Long getProcessoId() { return processoId; }
	Long getAgendamentoId() { return agendamentoId; }
	String getNomeOriginal() { return nomeOriginal; }
	long getTamanho() { return tamanho; }
	String getCaminho() { return caminho; }
	Long getAutorId() { return autorId; }
	Instant getCriadoEm() { return criadoEm; }
}
