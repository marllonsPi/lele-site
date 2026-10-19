package br.com.crispimcampos.gestao.cadastros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cliente")
class Cliente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nome;

	private String documento;
	private String email;
	private String telefone;
	private String endereco;
	private String observacoes;

	@Column(nullable = false)
	private boolean ativo = true;

	protected Cliente() {
	}

	Cliente(String nome, String documento, String email, String telefone, String endereco, String observacoes) {
		atualizar(nome, documento, email, telefone, endereco, observacoes);
	}

	void atualizar(String nome, String documento, String email, String telefone, String endereco, String observacoes) {
		this.nome = nome;
		this.documento = documento;
		this.email = email;
		this.telefone = telefone;
		this.endereco = endereco;
		this.observacoes = observacoes;
	}

	void desativar() {
		this.ativo = false;
	}

	void reativar() {
		this.ativo = true;
	}

	Long getId() { return id; }
	String getNome() { return nome; }
	String getDocumento() { return documento; }
	String getEmail() { return email; }
	String getTelefone() { return telefone; }
	String getEndereco() { return endereco; }
	String getObservacoes() { return observacoes; }
	boolean isAtivo() { return ativo; }
}
