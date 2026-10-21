package br.com.crispimcampos.gestao.cadastros;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;

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

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "cliente_area", joinColumns = @JoinColumn(name = "cliente_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "area", nullable = false)
	private Set<AreaAtuacao> areas = EnumSet.noneOf(AreaAtuacao.class);

	@Column(name = "a_conferir", nullable = false)
	private boolean aConferir;

	@Column(nullable = false)
	private String origem = "MANUAL";

	private String rg;

	@Column(name = "estado_civil")
	private String estadoCivil;

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

	void definirImportacao(boolean aConferir, String origem) {
		this.aConferir = aConferir;
		this.origem = origem;
	}

	void definirDadosCadastrais(String rg, String estadoCivil) {
		this.rg = rg;
		this.estadoCivil = estadoCivil;
	}

	void confirmarCadastro() {
		this.aConferir = false;
	}

	void definirAreas(Set<AreaAtuacao> novas) {
		areas.clear();
		areas.addAll(novas);
	}

	/** Cadastro incompleto: ainda sem area de atuacao (clientes anteriores a exigencia). */
	boolean incompleto() {
		return areas.isEmpty();
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
	Set<AreaAtuacao> getAreas() { return areas; }
	boolean isAConferir() { return aConferir; }
	String getRg() { return rg; }
	String getEstadoCivil() { return estadoCivil; }
	boolean isAtivo() { return ativo; }
}
