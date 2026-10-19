package br.com.crispimcampos.gestao.acesso;

import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ponto unico de leitura de publicacoes, agendamentos, processos e relatorios (invariante 1):
 * os modulos de dados filtram por {@link #titularesVisiveis}, nunca por regra propria.
 */
@Service
public class Visibilidade {

	private final RegraVisibilidadeRepository regras;

	Visibilidade(RegraVisibilidadeRepository regras) {
		this.regras = regras;
	}

	/** Titulares cujos itens o usuario pode ler. Para estagiario e sempre vazio: ele ve apenas o atribuido a ele. */
	@Transactional(readOnly = true)
	public Set<Long> titularesVisiveis(UsuarioLogado usuario, Escopo escopo) {
		List<ParVisibilidade> pares = regras.findByObservadorId(usuario.id()).stream()
				.map(r -> new ParVisibilidade(r.getObservadorId(), r.getTitularId(), r.getEscopo())).toList();
		return new HashSet<>(new PoliticaVisibilidade(pares).titularesVisiveis(usuario.id(), usuario.perfil(), escopo));
	}

	@Transactional(readOnly = true)
	public boolean podeVer(UsuarioLogado usuario, Escopo escopo, Long titularId) {
		return titularesVisiveis(usuario, escopo).contains(titularId);
	}
}
