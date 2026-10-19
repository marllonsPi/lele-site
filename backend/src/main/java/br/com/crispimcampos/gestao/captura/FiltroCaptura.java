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
	@Column(nullable = false, updatable = false)
	private Fonte fonte;

	@Column(name = "tribunal_prefixo", nullable = false, updatable = false)
	private String tribunalPrefixo;

	@Column(updatable = false)
	private String excecoes;

	@Column(nullable = false)
	private boolean ativo = true;

	protected FiltroCaptura() {
	}

	FiltroCaptura(Long usuarioId, Fonte fonte, String tribunalPrefixo, String excecoes) {
		this.usuarioId = usuarioId;
		this.fonte = fonte;
		this.tribunalPrefixo = tribunalPrefixo;
		this.excecoes = excecoes;
	}

	void desativar() {
		this.ativo = false;
	}

	Long getId() { return id; }
	Long getUsuarioId() { return usuarioId; }
	Fonte getFonte() { return fonte; }
	String getTribunalPrefixo() { return tribunalPrefixo; }
	String getExcecoes() { return excecoes; }
	boolean isAtivo() { return ativo; }
}
