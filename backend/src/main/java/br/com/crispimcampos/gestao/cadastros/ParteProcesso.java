package br.com.crispimcampos.gestao.cadastros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parte_processo")
class ParteProcesso {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "processo_id", nullable = false)
	private Long processoId;

	@Column(nullable = false)
	private String nome;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PapelParte papel;

	protected ParteProcesso() {
	}

	ParteProcesso(Long processoId, String nome, PapelParte papel) {
		this.processoId = processoId;
		this.nome = nome;
		this.papel = papel;
	}

	void anonimizar(String nomeNovo) {
		this.nome = nomeNovo;
	}

	Long getId() { return id; }
	String getNome() { return nome; }
	PapelParte getPapel() { return papel; }
}
