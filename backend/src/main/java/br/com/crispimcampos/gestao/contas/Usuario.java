package br.com.crispimcampos.gestao.contas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "usuario")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "senha_hash", nullable = false)
	private String senhaHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Perfil perfil;

	@Column(nullable = false)
	private boolean ativo = true;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "supervisor_id")
	private Usuario supervisor;

	@Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
	private Instant criadoEm;

	protected Usuario() {
	}

	public Usuario(String nome, String email, String senhaHash, Perfil perfil) {
		this.nome = nome;
		this.email = email == null ? null : email.trim().toLowerCase();
		this.senhaHash = senhaHash;
		this.perfil = perfil;
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public Perfil getPerfil() {
		return perfil;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void desativar() {
		this.ativo = false;
	}

	public void reativar() {
		this.ativo = true;
	}

	public void atualizar(String nome, String email, Perfil perfil) {
		this.nome = nome;
		this.email = email.trim().toLowerCase();
		this.perfil = perfil;
		if (perfil != Perfil.ESTAGIARIO) {
			this.supervisor = null;
		}
	}

	public void trocarSenha(String novoHash) {
		this.senhaHash = novoHash;
	}

	public Usuario getSupervisor() {
		return supervisor;
	}

	public void vincularSupervisor(Usuario supervisor) {
		if (perfil != Perfil.ESTAGIARIO) {
			throw new IllegalStateException("Somente estagiario tem supervisor");
		}
		this.supervisor = supervisor;
	}
}
