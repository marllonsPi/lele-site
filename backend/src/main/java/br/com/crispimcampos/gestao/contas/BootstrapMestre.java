package br.com.crispimcampos.gestao.contas;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante o acesso mestre a partir de variaveis de ambiente. Se o e-mail ainda nao existe, cria a conta (socia com
 * acesso mestre); se ja existe, apenas a promove e reativa, sem tocar na senha. Roda a cada partida e e idempotente.
 * Depois da primeira partida as variaveis podem sair do ambiente.
 */
@Component
@org.springframework.core.annotation.Order(2)
class BootstrapMestre implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BootstrapMestre.class);

	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;
	private final AuditoriaService auditoria;
	private final ApplicationEventPublisher eventos;
	private final String nome;
	private final String email;
	private final String senha;

	BootstrapMestre(UsuarioRepository usuarios, PasswordEncoder encoder, AuditoriaService auditoria,
			ApplicationEventPublisher eventos, @Value("${app.bootstrap.mestre.nome:}") String nome,
			@Value("${app.bootstrap.mestre.email:}") String email, @Value("${app.bootstrap.mestre.senha:}") String senha) {
		this.usuarios = usuarios;
		this.encoder = encoder;
		this.auditoria = auditoria;
		this.eventos = eventos;
		this.nome = nome;
		this.email = email;
		this.senha = senha;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (email.isBlank()) {
			return;
		}
		String login = email.trim().toLowerCase();
		var existente = usuarios.findByEmail(login);
		if (existente.isPresent()) {
			Usuario u = existente.get();
			if (u.getPerfil() != Perfil.SOCIA) {
				log.warn("Acesso mestre nao aplicado: a conta existente nao e socia");
				return;
			}
			if (!u.isMestre() || !u.isAtivo()) {
				u.definirMestre(true);
				u.reativar();
				auditoria.registrar(null, "BOOTSTRAP_MESTRE", "usuario", String.valueOf(u.getId()), "promovido");
				log.info("Acesso mestre garantido (id={})", u.getId());
			}
			return;
		}
		if (nome.isBlank() || senha.length() < UsuarioService.SENHA_MINIMA) {
			log.warn("Acesso mestre nao criado: informe nome e senha com ao menos {} caracteres", UsuarioService.SENHA_MINIMA);
			return;
		}
		Usuario mestre = new Usuario(nome.trim(), login, encoder.encode(senha), Perfil.SOCIA);
		mestre.definirMestre(true);
		// o mestre administra o sistema: nao recebe os alertas operacionais das socias, a menos que ligue depois
		mestre.atualizarPerfilProfissional(new DadosProfissionais(null, null, "Administrador do sistema", null, false, null));
		mestre = usuarios.save(mestre);
		auditoria.registrar(null, "BOOTSTRAP_MESTRE", "usuario", String.valueOf(mestre.getId()), "criado");
		log.info("Acesso mestre criado (id={})", mestre.getId());
	}
}
