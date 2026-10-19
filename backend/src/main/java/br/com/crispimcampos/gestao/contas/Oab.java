package br.com.crispimcampos.gestao.contas;

import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "oab")
public class Oab {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id")
	private Usuario usuario;

	@Column(nullable = false)
	private String numero;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 2, columnDefinition = "char(2)")
	private String uf;

	@Column(nullable = false)
	private boolean principal;

	@Column(nullable = false)
	private boolean ativa = true;

	protected Oab() {
	}

	public Oab(Usuario usuario, String numero, String uf, boolean principal) {
		this.usuario = usuario;
		this.numero = numero.replaceAll("\\D", "");
		this.uf = uf.toUpperCase();
		this.principal = principal;
	}

	public Long getId() {
		return id;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public String getNumero() {
		return numero;
	}

	public String getUf() {
		return uf;
	}

	public boolean isPrincipal() {
		return principal;
	}

	public boolean isAtiva() {
		return ativa;
	}

	void ativar() {
		this.ativa = true;
	}

	void desativar() {
		this.ativa = false;
		this.principal = false;
	}

	void definirPrincipal(boolean principal) {
		this.principal = principal;
	}
}
