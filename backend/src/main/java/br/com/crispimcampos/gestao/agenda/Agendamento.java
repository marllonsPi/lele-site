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

	/** Obrigatorio, exceto atendimento (ATD) ligado apenas ao cliente. */
	@Column(name = "processo_id", updatable = false)
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

	@Column(name = "inicio_em", updatable = false)
	private Instant inicioEm;

	@Column(name = "duracao_min")
	private Integer duracaoMin;

	@Enumerated(EnumType.STRING)
	private Modalidade modalidade;

	@Column(name = "local_ou_link")
	private String localOuLink;

	@Column(name = "cliente_id", updatable = false)
	private Long clienteId;

	@Column(name = "advogado_atendente_id")
	private Long advogadoAtendenteId;

	@Column(name = "finalizado_por")
	private Long finalizadoPor;

	@Column(name = "finalizado_em")
	private Instant finalizadoEm;

	protected Agendamento() {
	}

	/** Audiencia ou atendimento: data e hora proprias, sem a regra dos 3 dias uteis nem conferencia de prazo. */
	static Agendamento marcarCompromisso(Long processoId, Long titularId, TipoAgendamento tipo, String descricao,
			Long responsavelId, Long criadoPor, boolean urgente, Instant inicioEm, int duracaoMin,
			Modalidade modalidade, String localOuLink, Long clienteId, Long advogadoAtendenteId) {
		if (tipo.ehPrazo()) {
			throw new IllegalArgumentException("Prazo nao e compromisso com hora marcada");
		}
		Agendamento a = new Agendamento();
		a.processoId = processoId;
		a.titularId = titularId;
		a.tipo = tipo;
		a.descricao = descricao;
		a.responsavelId = responsavelId;
		a.criadoPor = criadoPor;
		a.urgente = urgente;
		a.status = StatusAgendamento.PENDENTE;
		a.inicioEm = inicioEm;
		a.duracaoMin = duracaoMin;
		a.modalidade = modalidade;
		a.localOuLink = localOuLink;
		a.clienteId = clienteId;
		a.advogadoAtendenteId = advogadoAtendenteId;
		return a;
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

	/**
	 * Responsavel conclui o trabalho (item 10). Exige prazo ja conferido (status PENDENTE) ou devolvido. Estagiario passa
	 * antes pelo supervisor; os demais seguem direto para a conferencia das socias.
	 */
	void finalizar(Long por, boolean porEstagiario, Instant quando) {
		if (status != StatusAgendamento.PENDENTE && status != StatusAgendamento.DEVOLVIDO) {
			throw new IllegalStateException("So e possivel finalizar item pendente ou devolvido");
		}
		this.finalizadoPor = por;
		this.finalizadoEm = quando;
		this.status = porEstagiario ? StatusAgendamento.EM_CONFERENCIA_SUPERVISOR : StatusAgendamento.EM_CONFERENCIA_SOCIA;
	}

	void aprovarPeloSupervisor() {
		if (status != StatusAgendamento.EM_CONFERENCIA_SUPERVISOR) {
			throw new IllegalStateException("O item nao aguarda o supervisor");
		}
		this.status = StatusAgendamento.EM_CONFERENCIA_SOCIA;
	}

	void conferirPelaSocia() {
		if (status != StatusAgendamento.EM_CONFERENCIA_SOCIA) {
			throw new IllegalStateException("O item nao aguarda conferencia das socias");
		}
		this.status = StatusAgendamento.CONFERIDO;
	}

	/** Volta ao responsavel (supervisor ou socia devolve); some da conferencia e reaparece na agenda dele. */
	void devolver() {
		if (status != StatusAgendamento.EM_CONFERENCIA_SUPERVISOR && status != StatusAgendamento.EM_CONFERENCIA_SOCIA) {
			throw new IllegalStateException("O item nao esta em conferencia");
		}
		this.status = StatusAgendamento.DEVOLVIDO;
	}

	Long getFinalizadoPor() { return finalizadoPor; }
	Instant getFinalizadoEm() { return finalizadoEm; }
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
	Instant getInicioEm() { return inicioEm; }
	Integer getDuracaoMin() { return duracaoMin; }
	Modalidade getModalidade() { return modalidade; }
	String getLocalOuLink() { return localOuLink; }
	Long getClienteId() { return clienteId; }
	Long getAdvogadoAtendenteId() { return advogadoAtendenteId; }
}
