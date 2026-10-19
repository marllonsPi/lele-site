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

/** O que cada fonte entregou, guardado inteiro (reprocessavel e auditavel), inclusive o que foi filtrado. */
@Entity
@Table(name = "publicacao_bruta")
class PublicacaoBruta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Fonte fonte;

	@Column(name = "id_externo", nullable = false, updatable = false)
	private String idExterno;

	@Column(name = "titular_id", nullable = false, updatable = false)
	private Long titularId;

	@Column(name = "numero_processo", updatable = false)
	private String numeroProcesso;

	@Column(updatable = false)
	private String tribunal;

	@Column(name = "data_publicacao", nullable = false, updatable = false)
	private LocalDate dataPublicacao;

	@Column(nullable = false, updatable = false)
	private String conteudo;

	@Column(nullable = false, updatable = false)
	private String hash;

	protected PublicacaoBruta() {
	}

	PublicacaoBruta(Long titularId, DadosBrutos d) {
		this.fonte = d.fonte();
		this.idExterno = d.idExterno();
		this.titularId = titularId;
		this.numeroProcesso = d.numeroProcesso();
		this.tribunal = d.tribunal();
		this.dataPublicacao = d.dataPublicacao();
		this.conteudo = d.conteudo();
		this.hash = d.hash();
	}

	Long getId() { return id; }
	Fonte getFonte() { return fonte; }
	String getNumeroProcesso() { return numeroProcesso; }
	String getTribunal() { return tribunal; }
	LocalDate getDataPublicacao() { return dataPublicacao; }
	String getConteudo() { return conteudo; }
	String getHash() { return hash; }
}
