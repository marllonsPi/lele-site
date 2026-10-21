package br.com.crispimcampos.gestao.captura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "filtro_captura")
class FiltroCaptura {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Fonte fonte;

	@Column(name = "tribunal_prefixo")
	private String tribunalPrefixo;

	@Column
	private String excecoes;

	@Column(nullable = false)
	private String modo = "IGNORAR";

	private String ufs;

	@Column(name = "oab_numero")
	private String oabNumero;

	@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
	@Column(name = "oab_uf", length = 2, columnDefinition = "char(2)")
	private String oabUf;

	@Column(nullable = false)
	private boolean ativo = true;

	protected FiltroCaptura() {
	}

	FiltroCaptura(Long usuarioId, Fonte fonte, String tribunalPrefixo, String excecoes) {
		this(usuarioId, fonte, tribunalPrefixo, excecoes, "IGNORAR", null, null, null);
	}

	FiltroCaptura(Long usuarioId, Fonte fonte, String tribunalPrefixo, String excecoes, String modo, String ufs, String oabNumero,
			String oabUf) {
		this.modo = modo;
		this.ufs = ufs;
		this.oabNumero = oabNumero;
		this.oabUf = oabUf;
		this.usuarioId = usuarioId;
		this.fonte = fonte;
		this.tribunalPrefixo = tribunalPrefixo;
		this.excecoes = excecoes;
	}

	void editar(Fonte fonte, String tribunalPrefixo, String excecoes, String modo, String ufs, String oabNumero, String oabUf,
			boolean ativo) {
		this.modo = modo;
		this.ufs = ufs;
		this.oabNumero = oabNumero;
		this.oabUf = oabUf;
		this.fonte = fonte;
		this.tribunalPrefixo = tribunalPrefixo;
		this.excecoes = excecoes;
		this.ativo = ativo;
	}

	Long getId() { return id; }
	Long getUsuarioId() { return usuarioId; }
	Fonte getFonte() { return fonte; }
	String getTribunalPrefixo() { return tribunalPrefixo; }
	String getExcecoes() { return excecoes; }
	String getModo() { return modo; }
	String getUfs() { return ufs; }
	String getOabNumero() { return oabNumero; }
	String getOabUf() { return oabUf; }
	boolean isAtivo() { return ativo; }
}
