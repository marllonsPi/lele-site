package br.com.crispimcampos.gestao.contas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Regras do acesso mestre: so um mestre altera outro mestre; nunca fica sem mestre ativo; mestre e sempre socia. */
class AcessoMestreTest {

	static final String SENHA = "senha-de-teste-123";
	static final long MESTRE = 1L;
	static final long SOCIA = 2L;
	static final long OUTRO_MESTRE = 3L;

	UsuarioRepository usuarios = mock(UsuarioRepository.class);
	OabRepository oabs = mock(OabRepository.class);
	UsuarioService servico = new UsuarioService(usuarios, oabs, new BCryptPasswordEncoder(4), mock(AuditoriaService.class),
			mock(ApplicationEventPublisher.class));

	Usuario mestre = conta(MESTRE, Perfil.SOCIA, true);
	Usuario socia = conta(SOCIA, Perfil.SOCIA, false);
	Usuario outroMestre = conta(OUTRO_MESTRE, Perfil.SOCIA, true);

	Usuario conta(long id, Perfil perfil, boolean ehMestre) {
		Usuario u = new Usuario("Fulano " + id, "u" + id + "@exemplo.test", "hash", perfil);
		ReflectionTestUtils.setField(u, "id", id);
		u.definirMestre(ehMestre);
		when(usuarios.findById(id)).thenReturn(Optional.of(u));
		return u;
	}

	@BeforeEach
	void prepara() {
		when(usuarios.save(any())).thenAnswer(i -> {
			Usuario u = i.getArgument(0);
			if (u.getId() == null) {
				ReflectionTestUtils.setField(u, "id", 100L);
			}
			return u;
		});
		when(oabs.findByUsuarioIdOrderByPrincipalDescIdAsc(any())).thenReturn(List.of());
		when(usuarios.countByPerfilAndAtivoTrue(Perfil.SOCIA)).thenReturn(3L);
		when(usuarios.countByMestreTrueAndAtivoTrue()).thenReturn(2L);
	}

	@Test
	void sociaComumNaoAlteraNemDesativaNemRedefineSenhaDoMestre() {
		assertThatThrownBy(() -> servico.desativar(SOCIA, MESTRE)).isInstanceOf(RegraDeContaException.class);
		assertThatThrownBy(() -> servico.redefinirSenha(SOCIA, MESTRE, SENHA)).isInstanceOf(RegraDeContaException.class);
		assertThatThrownBy(() -> servico.atualizar(SOCIA, MESTRE, "X", "x@exemplo.test", Perfil.SOCIA, null))
				.isInstanceOf(RegraDeContaException.class);
		assertThatThrownBy(() -> servico.adicionarOab(SOCIA, MESTRE, "123", "DF", true)).isInstanceOf(RegraDeContaException.class);
		assertThat(mestre.isAtivo()).isTrue();
		assertThat(mestre.getSenhaHash()).isEqualTo("hash");
	}

	@Test
	void mestreAlteraQualquerContaInclusiveOutroMestre() {
		servico.redefinirSenha(MESTRE, SOCIA, SENHA);
		servico.redefinirSenha(MESTRE, OUTRO_MESTRE, SENHA);
		servico.desativar(MESTRE, OUTRO_MESTRE);
		assertThat(outroMestre.isAtivo()).isFalse();
	}

	@Test
	void sociaComumNaoCriaNemConcedeAcessoMestre() {
		assertThatThrownBy(() -> servico.criar(SOCIA, "N", "n@exemplo.test", SENHA, Perfil.SOCIA, null, DadosProfissionais.vazio(), true))
				.isInstanceOf(RegraDeContaException.class);
		assertThatThrownBy(() -> servico.atualizar(SOCIA, SOCIA, "S", "s@exemplo.test", Perfil.SOCIA, null, null, true))
				.isInstanceOf(RegraDeContaException.class);
		assertThat(socia.isMestre()).isFalse();
	}

	@Test
	void mestreConcedeERetiraAcessoMasNaoDeSiNemDoUltimo() {
		servico.atualizar(MESTRE, SOCIA, "S", "s@exemplo.test", Perfil.SOCIA, null, null, true);
		assertThat(socia.isMestre()).isTrue();
		assertThatThrownBy(() -> servico.atualizar(MESTRE, MESTRE, "M", "m@exemplo.test", Perfil.SOCIA, null, null, false))
				.hasMessageContaining("proprio");
		when(usuarios.countByMestreTrueAndAtivoTrue()).thenReturn(1L);
		assertThatThrownBy(() -> servico.atualizar(MESTRE, SOCIA, "S", "s@exemplo.test", Perfil.SOCIA, null, null, false))
				.hasMessageContaining("ao menos um acesso mestre");
		assertThatThrownBy(() -> servico.desativar(OUTRO_MESTRE, MESTRE)).hasMessageContaining("ao menos um acesso mestre");
	}

	@Test
	void mestreDeveSerSocia() {
		assertThatThrownBy(() -> servico.criar(MESTRE, "N", "n@exemplo.test", SENHA, Perfil.ADVOGADO, null, DadosProfissionais.vazio(), true))
				.isInstanceOf(RegraDeContaException.class);
		assertThatThrownBy(() -> servico.atualizar(MESTRE, OUTRO_MESTRE, "O", "o@exemplo.test", Perfil.ADVOGADO, null, null, true))
				.isInstanceOf(RegraDeContaException.class);
	}

	@Test
	void guardaDadosProfissionaisEDestinoDosAlertas() {
		var dados = new DadosProfissionais(" 61 99999-0000 ", "", "Advogada", " ALERTAS@Exemplo.test ", true, null);
		var dto = servico.criar(MESTRE, "Dra. Teste", "dra@exemplo.test", SENHA, Perfil.ADVOGADO, null, dados, false);
		assertThat(dto.telefone()).isEqualTo("61 99999-0000");
		assertThat(dto.whatsapp()).isNull();
		assertThat(dto.destinoAlertas()).isEqualTo("alertas@exemplo.test");
		var semAlerta = servico.atualizarMeuPerfil(SOCIA, new DadosProfissionais(null, null, null, null, false, null));
		assertThat(semAlerta.destinoAlertas()).isNull();
	}

	@Test
	void trocaDaPropriaSenhaExigeASenhaAtual() {
		socia.trocarSenha(new BCryptPasswordEncoder(4).encode("senha-atual-123"));
		assertThatThrownBy(() -> servico.trocarMinhaSenha(SOCIA, "errada-errada", "nova-senha-1234")).hasMessageContaining("atual incorreta");
		assertThatThrownBy(() -> servico.trocarMinhaSenha(SOCIA, "senha-atual-123", "curta")).isInstanceOf(RegraDeContaException.class);
		servico.trocarMinhaSenha(SOCIA, "senha-atual-123", "nova-senha-1234");
		assertThat(new BCryptPasswordEncoder(4).matches("nova-senha-1234", socia.getSenhaHash())).isTrue();
	}
}
