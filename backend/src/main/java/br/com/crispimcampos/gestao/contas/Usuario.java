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

	@Column(name = "totp_secret")
	private String totpSecret;

	@Column(name = "totp_ativo", nullable = false)
	private boolean totpAtivo;

	@Column(nullable = false)
	private boolean mestre;

	private String telefone;

	private String whatsapp;

	private String cargo;

	@Column(name = "email_alertas")
	private String emailAlertas;

	@Column(name = "alertas_email", nullable = false)
	private boolean alertasEmail = true;

	private String assinatura;

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

	public boolean isMestre() {
		return mestre;
	}

	void definirMestre(boolean mestre) {
		if (mestre && perfil != Perfil.SOCIA) {
			throw new IllegalStateException("Somente socia pode ter acesso mestre");
		}
		this.mestre = mestre;
	}

	public String getTelefone() {
		return telefone;
	}

	public String getWhatsapp() {
		return whatsapp;
	}

	public String getCargo() {
		return cargo;
	}

	public String getEmailAlertas() {
		return emailAlertas;
	}

	public boolean isAlertasEmail() {
		return alertasEmail;
	}

	public String getAssinatura() {
		return assinatura;
	}

	/** Dados de contato e atuacao da conta; texto vazio vira ausente. */
	void atualizarPerfilProfissional(DadosProfissionais d) {
		this.telefone = limpo(d.telefone());
		this.whatsapp = limpo(d.whatsapp());
		this.cargo = limpo(d.cargo());
		this.emailAlertas = d.emailAlertas() == null || d.emailAlertas().isBlank() ? null : d.emailAlertas().trim().toLowerCase();
		this.alertasEmail = d.alertasEmail() == null || d.alertasEmail();
		this.assinatura = limpo(d.assinatura());
	}

	private static String limpo(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
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

	String getTotpSecret() {
		return totpSecret;
	}

	boolean isTotpAtivo() {
		return totpAtivo;
	}

	/** Gera um segredo novo e deixa o 2FA inativo ate a confirmacao com um codigo valido. */
	void iniciarDoisFatores(String segredo) {
		this.totpSecret = segredo;
		this.totpAtivo = false;
	}

	void ativarDoisFatores() {
		if (totpSecret == null) {
			throw new IllegalStateException("Inicie o cadastro do segundo fator primeiro");
		}
		this.totpAtivo = true;
	}

	void desativarDoisFatores() {
		this.totpSecret = null;
		this.totpAtivo = false;
	}
}
