package br.com.crispimcampos.gestao.calendario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "feriado")
class Feriado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EscopoFeriado escopo;

	private String referencia;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoFeriado tipo;

	@Column(name = "data_inicial", nullable = false)
	private LocalDate dataInicial;

	@Column(name = "data_final", nullable = false)
	private LocalDate dataFinal;

	@Column(nullable = false)
	private String descricao;

	@Column(nullable = false)
	private boolean ativo = true;

	protected Feriado() {
	}

	Feriado(EscopoFeriado escopo, String referencia, TipoFeriado tipo, LocalDate dataInicial, LocalDate dataFinal,
			String descricao) {
		atualizar(escopo, referencia, tipo, dataInicial, dataFinal, descricao);
	}

	void atualizar(EscopoFeriado escopo, String referencia, TipoFeriado tipo, LocalDate dataInicial,
			LocalDate dataFinal, String descricao) {
		this.escopo = escopo;
		this.referencia = referencia;
		this.tipo = tipo;
		this.dataInicial = dataInicial;
		this.dataFinal = dataFinal;
		this.descricao = descricao;
	}

	void desativar() {
		this.ativo = false;
	}

	Long getId() { return id; }
	EscopoFeriado getEscopo() { return escopo; }
	String getReferencia() { return referencia; }
	TipoFeriado getTipo() { return tipo; }
	LocalDate getDataInicial() { return dataInicial; }
	LocalDate getDataFinal() { return dataFinal; }
	String getDescricao() { return descricao; }
	boolean isAtivo() { return ativo; }
}
