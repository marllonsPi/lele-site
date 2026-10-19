package br.com.crispimcampos.gestao.contas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioServiceTest {

	UsuarioRepository usuarios = mock(UsuarioRepository.class);
	OabRepository oabs = mock(OabRepository.class);
	ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
	UsuarioService servico = new UsuarioService(usuarios, oabs, new BCryptPasswordEncoder(4),
			mock(AuditoriaService.class), eventos);

	static final String SENHA = "senha-de-teste-123";

	Usuario usuario(long id, Perfil perfil) {
		Usuario u = new Usuario("Fulano " + id, "u" + id + "@exemplo.test", "hash", perfil);
		ReflectionTestUtils.setField(u, "id", id);
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
		when(oabs.save(any())).thenAnswer(i -> {
			Oab o = i.getArgument(0);
			ReflectionTestUtils.setField(o, "id", 200L);
			return o;
		});
		when(oabs.findByUsuarioIdOrderByPrincipalDescIdAsc(any())).thenReturn(List.of());
	}

	@Test
	void criaUsuarioPublicaEventoEGuardaSenhaComHash() {
		var dto = servico.criar(1L, " Luana Teste ", "LUANA@Exemplo.test", SENHA, Perfil.ASSISTENTE, null);
		assertThat(dto.email()).isEqualTo("luana@exemplo.test");
		assertThat(dto.perfil()).isEqualTo(Perfil.ASSISTENTE);
		ArgumentCaptor<Usuario> c = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarios).save(c.capture());
		assertThat(c.getValue().getSenhaHash()).isNotEqualTo(SENHA).startsWith("$2");
		verify(eventos).publishEvent(new UsuarioCriado(100L, Perfil.ASSISTENTE));
	}

	@Test
	void recusaSenhaCurtaEEmailDuplicado() {
		assertThatThrownBy(() -> servico.criar(1L, "A", "a@exemplo.test", "curta", Perfil.ADVOGADO, null))
				.isInstanceOf(RegraDeContaException.class);
		Usuario existente = usuario(2, Perfil.ADVOGADO);
		when(usuarios.findByEmail("a@exemplo.test")).thenReturn(Optional.of(existente));
		assertThatThrownBy(() -> servico.criar(1L, "A", "a@exemplo.test", SENHA, Perfil.ADVOGADO, null))
				.isInstanceOf(RegraDeContaException.class).extracting("conflito").isEqualTo(true);
	}

	@Test
	void estagiarioExigeSupervisorAdvogadoAtivo() {
		assertThatThrownBy(() -> servico.criar(1L, "E", "e@exemplo.test", SENHA, Perfil.ESTAGIARIO, null))
				.isInstanceOf(RegraDeContaException.class);
		usuario(3, Perfil.ASSISTENTE);
		assertThatThrownBy(() -> servico.criar(1L, "E", "e@exemplo.test", SENHA, Perfil.ESTAGIARIO, 3L))
				.isInstanceOf(RegraDeContaException.class);
		usuario(4, Perfil.ADVOGADO);
		var dto = servico.criar(1L, "E", "e@exemplo.test", SENHA, Perfil.ESTAGIARIO, 4L);
		assertThat(dto.supervisorId()).isEqualTo(4L);
	}

	@Test
	void supervisorSoParaEstagiario() {
		usuario(4, Perfil.ADVOGADO);
		assertThatThrownBy(() -> servico.criar(1L, "A", "a@exemplo.test", SENHA, Perfil.ADVOGADO, 4L))
				.isInstanceOf(RegraDeContaException.class);
	}

	@Test
	void naoDesativaASiMesmoNemAUltimaSocia() {
		usuario(1, Perfil.SOCIA);
		assertThatThrownBy(() -> servico.desativar(1L, 1L)).isInstanceOf(RegraDeContaException.class);
		usuario(2, Perfil.SOCIA);
		when(usuarios.countByPerfilAndAtivoTrue(Perfil.SOCIA)).thenReturn(1L);
		assertThatThrownBy(() -> servico.desativar(1L, 2L)).isInstanceOf(RegraDeContaException.class);
		when(usuarios.countByPerfilAndAtivoTrue(Perfil.SOCIA)).thenReturn(2L);
		servico.desativar(1L, 2L);
		assertThat(usuarios.findById(2L).get().isAtivo()).isFalse();
	}

	@Test
	void naoDesativaSupervisorComEstagiariosAtivos() {
		usuario(4, Perfil.ADVOGADO);
		Usuario estagiario = usuario(6, Perfil.ESTAGIARIO);
		when(usuarios.findBySupervisorIdAndAtivoTrue(4L)).thenReturn(List.of(estagiario));
		assertThatThrownBy(() -> servico.desativar(1L, 4L)).isInstanceOf(RegraDeContaException.class);
	}

	@Test
	void oabSoParaSociaOuAdvogadoENormalizaNumero() {
		usuario(5, Perfil.ASSISTENTE);
		assertThatThrownBy(() -> servico.adicionarOab(1L, 5L, "1", "DF", true)).isInstanceOf(RegraDeContaException.class);
		usuario(3, Perfil.ADVOGADO);
		var oab = servico.adicionarOab(1L, 3L, "83.736", "df", false);
		assertThat(oab.numero()).isEqualTo("83736");
		assertThat(oab.uf()).isEqualTo("DF");
		assertThat(oab.principal()).as("primeira OAB vira principal").isTrue();
		verify(eventos).publishEvent(new OabAtivada(200L, 3L, "83736", "DF"));
	}

	@Test
	void oabDuplicadaEUfInvalidaSaoRecusadas() {
		usuario(3, Perfil.ADVOGADO);
		when(oabs.existsByNumeroAndUf("84089", "DF")).thenReturn(true);
		assertThatThrownBy(() -> servico.adicionarOab(1L, 3L, "84089", "DF", false))
				.isInstanceOf(RegraDeContaException.class).extracting("conflito").isEqualTo(true);
		assertThatThrownBy(() -> servico.adicionarOab(1L, 3L, "84089", "D1", false)).isInstanceOf(RegraDeContaException.class);
	}

	@Test
	void naoAlteraOProprioPerfil() {
		usuario(1, Perfil.SOCIA);
		assertThatThrownBy(() -> servico.atualizar(1L, 1L, "X", "x@exemplo.test", Perfil.ADVOGADO, null))
				.isInstanceOf(RegraDeContaException.class);
	}
}
