package br.com.crispimcampos.gestao.cadastros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "processo")
class Processo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 20, columnDefinition = "char(20)", updatable = false)
	private String numero;

	@Column(nullable = false)
	private String tribunal;

	@Column(name = "vara_comarca")
	private String varaComarca;

	private String classe;
	private String situacao;

	@Column(name = "valor_causa")
	private BigDecimal valorCausa;

	@Enumerated(EnumType.STRING)
	private AreaAtuacao area;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cliente_id")
	private Cliente cliente;

	/** Titular do processo para a visibilidade (item 2). */
	@Column(name = "advogado_id", nullable = false)
	private Long advogadoId;

	@Column(nullable = false)
	private boolean ativo = true;

	protected Processo() {
	}

	Processo(String numero, Cliente cliente, Long advogadoId) {
		this.numero = numero;
		this.cliente = cliente;
		this.advogadoId = advogadoId;
	}

	void atualizar(String tribunal, String varaComarca, String classe, String situacao, BigDecimal valorCausa,
			Cliente cliente, Long advogadoId) {
		this.tribunal = tribunal;
		this.varaComarca = varaComarca;
		this.classe = classe;
		this.situacao = situacao;
		this.valorCausa = valorCausa;
		this.cliente = cliente;
		this.advogadoId = advogadoId;
	}

	void definirArea(AreaAtuacao area) {
		this.area = area;
	}

	void desativar() {
		this.ativo = false;
	}

	Long getId() { return id; }
	String getNumero() { return numero; }
	String getTribunal() { return tribunal; }
	String getVaraComarca() { return varaComarca; }
	String getClasse() { return classe; }
	String getSituacao() { return situacao; }
	BigDecimal getValorCausa() { return valorCausa; }
	AreaAtuacao getArea() { return area; }
	Cliente getCliente() { return cliente; }
	Long getAdvogadoId() { return advogadoId; }
	boolean isAtivo() { return ativo; }
}
