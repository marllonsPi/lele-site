package br.com.crispimcampos.gestao.acesso;

import br.com.crispimcampos.gestao.contas.Perfil;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Regra de visibilidade (item 2) sem dependencia de framework ou banco: dado o conjunto de regras e o
 * observador, devolve em nome de quais titulares ele pode ler. E o unico lugar que decide isso.
 * Estagiario nunca recebe titulares: so enxerga o que lhe foi atribuido (decisao da agenda, Fase 4/5).
 * Financeiro nao passa por aqui: e restrito ao perfil Socia.
 */
public final class PoliticaVisibilidade {

	private final Set<ParVisibilidade> regras;

	public PoliticaVisibilidade(Collection<ParVisibilidade> regras) {
		this.regras = new HashSet<>(regras);
	}

	public Set<Long> titularesVisiveis(Long observadorId, Perfil perfil, Escopo escopo) {
		Set<Long> titulares = new HashSet<>();
		if (perfil == Perfil.ESTAGIARIO) {
			return titulares;
		}
		for (ParVisibilidade r : regras) {
			if (r.observadorId().equals(observadorId) && r.escopo() == escopo) {
				titulares.add(r.titularId());
			}
		}
		return titulares;
	}

	public boolean podeVer(Long observadorId, Perfil perfil, Escopo escopo, Long titularId) {
		return titularesVisiveis(observadorId, perfil, escopo).contains(titularId);
	}
}
