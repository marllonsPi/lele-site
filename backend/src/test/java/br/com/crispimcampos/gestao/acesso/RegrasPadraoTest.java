package br.com.crispimcampos.gestao.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioCriado;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegrasPadraoTest {

	RegraVisibilidadeRepository repo = mock(RegraVisibilidadeRepository.class);
	UsuarioService usuarios = mock(UsuarioService.class);
	MatrizVisibilidadeService matriz = new MatrizVisibilidadeService(repo, usuarios, mock(AuditoriaService.class));
	List<RegraVisibilidade> salvas = new ArrayList<>();

	static UsuarioDto u(long id, Perfil p) {
		return new UsuarioDto(id, "Fulano " + id, "u" + id + "@exemplo.test", p, true, null, List.of());
	}

	@BeforeEach
	void prepara() {
		when(repo.save(any())).thenAnswer(i -> {
			salvas.add(i.getArgument(0));
			return i.getArgument(0);
		});
	}

	boolean tem(long obs, long tit, Escopo e) {
		return salvas.stream().anyMatch(r -> r.getObservadorId() == obs && r.getTitularId() == tit && r.getEscopo() == e);
	}

	@Test
	void novaSociaVeTodosEOsTodasSociasAVeem() {
		when(usuarios.listar()).thenReturn(List.of(u(1, Perfil.SOCIA), u(2, Perfil.ADVOGADO), u(3, Perfil.ASSISTENTE), u(9, Perfil.SOCIA)));
		matriz.aoCriarUsuario(new UsuarioCriado(9L, Perfil.SOCIA));
		for (Escopo e : Escopo.values()) {
			assertThat(tem(9, 9, e)).isTrue();
			assertThat(tem(9, 1, e)).isTrue();
			assertThat(tem(9, 2, e)).isTrue();
			assertThat(tem(1, 9, e)).isTrue();
			assertThat(tem(9, 3, e)).as("socia nao precisa ver a assistente").isFalse();
		}
	}

	@Test
	void novoAdvogadoVeSoOProprioESociasOVeem() {
		when(usuarios.listar()).thenReturn(List.of(u(1, Perfil.SOCIA), u(2, Perfil.ADVOGADO), u(7, Perfil.ADVOGADO)));
		matriz.aoCriarUsuario(new UsuarioCriado(7L, Perfil.ADVOGADO));
		for (Escopo e : Escopo.values()) {
			assertThat(tem(7, 7, e)).isTrue();
			assertThat(tem(1, 7, e)).isTrue();
			assertThat(tem(7, 2, e)).isFalse();
			assertThat(tem(2, 7, e)).as("advogado nao ve advogado").isFalse();
		}
	}

	@Test
	void assistenteEEstagiarioComecamSemRegras() {
		matriz.aoCriarUsuario(new UsuarioCriado(5L, Perfil.ASSISTENTE));
		matriz.aoCriarUsuario(new UsuarioCriado(6L, Perfil.ESTAGIARIO));
		assertThat(salvas).isEmpty();
		verifyNoInteractions(usuarios);
	}

	@Test
	void estagiarioNaoPodeReceberRegraNaMatriz() {
		when(usuarios.buscar(6L)).thenReturn(u(6, Perfil.ESTAGIARIO));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> matriz.definirLinha(1L, 6L,
				java.util.Set.of(new ParVisibilidade(6L, 1L, Escopo.PUBLICACAO))));
	}

	@Test
	void linhaSoAceitaRegrasDoProprioObservadorETitularesExistentes() {
		when(usuarios.buscar(5L)).thenReturn(u(5, Perfil.ASSISTENTE));
		when(usuarios.listar()).thenReturn(List.of(u(1, Perfil.SOCIA), u(5, Perfil.ASSISTENTE)));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> matriz.definirLinha(1L, 5L,
				java.util.Set.of(new ParVisibilidade(4L, 1L, Escopo.PUBLICACAO))));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> matriz.definirLinha(1L, 5L,
				java.util.Set.of(new ParVisibilidade(5L, 77L, Escopo.PUBLICACAO))));
		matriz.definirLinha(1L, 5L, java.util.Set.of(new ParVisibilidade(5L, 1L, Escopo.PUBLICACAO)));
		ArgumentCaptor<RegraVisibilidade> c = ArgumentCaptor.forClass(RegraVisibilidade.class);
		verify(repo).save(c.capture());
		assertThat(c.getValue().getTitularId()).isEqualTo(1L);
	}
}
