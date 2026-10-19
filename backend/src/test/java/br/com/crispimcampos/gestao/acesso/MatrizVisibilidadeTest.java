package br.com.crispimcampos.gestao.acesso;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.crispimcampos.gestao.contas.Perfil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Matriz de docs/03 (item 2 da spec) com usuarios ficticios. Qualquer mudanca de visibilidade
 * precisa alterar este teste (invariante 1).
 */
class MatrizVisibilidadeTest {

	static final Long LETICIA = 1L, LORRAYNNY = 2L, ANDRESSA = 3L, WENDELL = 4L, LUANA = 5L, ESTAGIARIO = 6L;

	/** Seed da spec: o que a equipe configura pela tela de visibilidade. */
	static List<ParVisibilidade> seed() {
		List<ParVisibilidade> r = new ArrayList<>();
		for (Escopo e : Escopo.values()) {
			for (Long socia : List.of(LETICIA, LORRAYNNY)) {
				for (Long titular : List.of(LETICIA, LORRAYNNY, ANDRESSA, WENDELL)) {
					r.add(new ParVisibilidade(socia, titular, e));
				}
			}
			r.add(new ParVisibilidade(ANDRESSA, ANDRESSA, e));
			r.add(new ParVisibilidade(WENDELL, WENDELL, e));
			for (Long titular : List.of(LETICIA, LORRAYNNY, ANDRESSA)) {
				r.add(new ParVisibilidade(LUANA, titular, e));
			}
		}
		return r;
	}

	final PoliticaVisibilidade politica = new PoliticaVisibilidade(seed());

	Set<Long> ve(Long observador, Perfil perfil, Escopo escopo) {
		return politica.titularesVisiveis(observador, perfil, escopo);
	}

	@Test
	void sociasVeemTodosOsAdvogadosEAsDuasSocias() {
		for (Escopo e : Escopo.values()) {
			assertThat(ve(LETICIA, Perfil.SOCIA, e)).containsExactlyInAnyOrder(LETICIA, LORRAYNNY, ANDRESSA, WENDELL);
			assertThat(ve(LORRAYNNY, Perfil.SOCIA, e)).containsExactlyInAnyOrder(LETICIA, LORRAYNNY, ANDRESSA, WENDELL);
		}
	}

	@Test
	void andressaVeSomenteOProprio() {
		for (Escopo e : Escopo.values()) {
			assertThat(ve(ANDRESSA, Perfil.ADVOGADO, e)).containsExactly(ANDRESSA);
		}
	}

	@Test
	void wendellVeSomenteOProprio() {
		for (Escopo e : Escopo.values()) {
			assertThat(ve(WENDELL, Perfil.ADVOGADO, e)).containsExactly(WENDELL);
		}
	}

	@Test
	void luanaVeLeticiaLorraynnyEAndressaMasNuncaWendell() {
		for (Escopo e : Escopo.values()) {
			assertThat(ve(LUANA, Perfil.ASSISTENTE, e)).containsExactlyInAnyOrder(LETICIA, LORRAYNNY, ANDRESSA);
			assertThat(politica.podeVer(LUANA, Perfil.ASSISTENTE, e, WENDELL)).isFalse();
		}
	}

	@Test
	void advogadosNaoVeemSocias() {
		assertThat(politica.podeVer(ANDRESSA, Perfil.ADVOGADO, Escopo.PUBLICACAO, LETICIA)).isFalse();
		assertThat(politica.podeVer(WENDELL, Perfil.ADVOGADO, Escopo.AGENDAMENTO, LORRAYNNY)).isFalse();
		assertThat(politica.podeVer(ANDRESSA, Perfil.ADVOGADO, Escopo.PROCESSO, WENDELL)).isFalse();
	}

	@Test
	void estagiarioNaoRecebeTitularesNemComRegraEmTabela() {
		var comRegraIndevida = new ArrayList<>(seed());
		comRegraIndevida.add(new ParVisibilidade(ESTAGIARIO, LETICIA, Escopo.PUBLICACAO));
		var p = new PoliticaVisibilidade(comRegraIndevida);
		assertThat(p.titularesVisiveis(ESTAGIARIO, Perfil.ESTAGIARIO, Escopo.PUBLICACAO)).isEmpty();
	}

	@Test
	void visibilidadeEhConfiguravelPorDados() {
		var ajustada = new ArrayList<>(seed());
		ajustada.add(new ParVisibilidade(LUANA, WENDELL, Escopo.AGENDAMENTO));
		var p = new PoliticaVisibilidade(ajustada);
		assertThat(p.podeVer(LUANA, Perfil.ASSISTENTE, Escopo.AGENDAMENTO, WENDELL)).isTrue();
		assertThat(p.podeVer(LUANA, Perfil.ASSISTENTE, Escopo.PUBLICACAO, WENDELL)).isFalse();
	}

	@Test
	void usuarioSemRegrasNaoVeNada() {
		assertThat(ve(99L, Perfil.ASSISTENTE, Escopo.PROCESSO)).isEmpty();
	}
}
