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

/** Cria a primeira socia a partir de variaveis de ambiente, apenas com a base vazia. */
@Component
@org.springframework.core.annotation.Order(1)
class BootstrapSocia implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BootstrapSocia.class);
	static final int SENHA_MINIMA = 12;

	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;
	private final AuditoriaService auditoria;
	private final ApplicationEventPublisher eventos;
	private final String nome;
	private final String email;
	private final String senha;

	BootstrapSocia(UsuarioRepository usuarios, PasswordEncoder encoder, AuditoriaService auditoria, ApplicationEventPublisher eventos,
			@Value("${app.bootstrap.socia.nome:}") String nome,
			@Value("${app.bootstrap.socia.email:}") String email,
			@Value("${app.bootstrap.socia.senha:}") String senha) {
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
		if (nome.isBlank() || email.isBlank() || senha.isBlank() || usuarios.count() > 0) {
			return;
		}
		if (senha.length() < SENHA_MINIMA) {
			throw new IllegalStateException("BOOTSTRAP_SOCIA_SENHA deve ter ao menos " + SENHA_MINIMA + " caracteres");
		}
		Usuario socia = usuarios.save(new Usuario(nome, email, encoder.encode(senha), Perfil.SOCIA));
		auditoria.registrar(null, "BOOTSTRAP_SOCIA", "usuario", String.valueOf(socia.getId()), null);
		eventos.publishEvent(new UsuarioCriado(socia.getId(), Perfil.SOCIA)); // concede as regras de visibilidade proprias
		log.info("Primeira socia criada (id={})", socia.getId());
	}
}
