package br.com.crispimcampos.gestao.agenda;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "agendamento")
class Agendamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "processo_id", nullable = false, updatable = false)
	private Long processoId;

	/** Em nome de quem esta (advogado do processo no momento do lancamento): base da visibilidade. */
	@Column(name = "titular_id", nullable = false, updatable = false)
	private Long titularId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private TipoAgendamento tipo;

	private String descricao;

	@Column(name = "responsavel_id", nullable = false)
	private Long responsavelId;

	@Column(name = "criado_por", nullable = false, updatable = false)
	private Long criadoPor;

	@Column(nullable = false)
	private boolean urgente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StatusAgendamento status;

	@Column(name = "inicio_prazo")
	private LocalDate inicioPrazo;

	@Column(name = "prazo_interno")
	private LocalDate prazoInterno;

	@Column(name = "prazo_fatal")
	private LocalDate prazoFatal;

	@Column(name = "lancado_por")
	private Long lancadoPor;

	@Column(name = "conferido_por")
	private Long conferidoPor;

	@Column(name = "conferido_em")
	private Instant conferidoEm;

	protected Agendamento() {
	}

	/** Prazo recem-lancado: sempre aguarda a conferencia de outra pessoa. */
	static Agendamento lancarPrazo(Long processoId, Long titularId, TipoAgendamento tipo, String descricao,
			Long responsavelId, Long lancadoPor, boolean urgente, LocalDate inicio, LocalDate interno, LocalDate fatal) {
		Agendamento a = new Agendamento();
		a.processoId = processoId;
		a.titularId = titularId;
		a.tipo = tipo;
		a.descricao = descricao;
		a.responsavelId = responsavelId;
		a.criadoPor = lancadoPor;
		a.lancadoPor = lancadoPor;
		a.urgente = urgente;
		a.status = StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO;
		a.inicioPrazo = inicio;
		a.prazoInterno = interno;
		a.prazoFatal = fatal;
		return a;
	}

	/** Correcao da contagem enquanto aguarda conferencia (nunca depois). */
	void corrigirPrazo(String descricao, Long responsavelId, boolean urgente, LocalDate inicio, LocalDate interno,
			LocalDate fatal) {
		if (status != StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO) {
			throw new IllegalStateException("Prazo ja conferido nao pode ser alterado");
		}
		this.descricao = descricao;
		this.responsavelId = responsavelId;
		this.urgente = urgente;
		this.inicioPrazo = inicio;
		this.prazoInterno = interno;
		this.prazoFatal = fatal;
	}

	/** Dupla conferencia (invariante 2): quem lancou nao confirma; so confirma o que aguarda. */
	void conferirPrazo(Long conferente, Instant quando) {
		if (status != StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO) {
			throw new IllegalStateException("O prazo nao aguarda conferencia");
		}
		if (conferente.equals(lancadoPor)) {
			throw new IllegalStateException("Quem lancou o prazo nao pode confirma-lo");
		}
		this.conferidoPor = conferente;
		this.conferidoEm = quando;
		this.status = StatusAgendamento.PENDENTE;
	}

	Long getId() { return id; }
	Long getProcessoId() { return processoId; }
	Long getTitularId() { return titularId; }
	TipoAgendamento getTipo() { return tipo; }
	String getDescricao() { return descricao; }
	Long getResponsavelId() { return responsavelId; }
	Long getCriadoPor() { return criadoPor; }
	boolean isUrgente() { return urgente; }
	StatusAgendamento getStatus() { return status; }
	LocalDate getInicioPrazo() { return inicioPrazo; }
	LocalDate getPrazoInterno() { return prazoInterno; }
	LocalDate getPrazoFatal() { return prazoFatal; }
	Long getLancadoPor() { return lancadoPor; }
	Long getConferidoPor() { return conferidoPor; }
	Instant getConferidoEm() { return conferidoEm; }
}
