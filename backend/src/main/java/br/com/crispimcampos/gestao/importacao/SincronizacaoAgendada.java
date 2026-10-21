package br.com.crispimcampos.gestao.importacao;

import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Sincronizacao diaria com a planilha e o Drive. Desligada por padrao: {@code app.importacao.sincronizacao-diaria=true} e
 * {@code app.importacao.conta-usuario-id} = socia cuja conta Google (com acesso ao Drive) sera usada. Assume 1 instancia do backend.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.importacao.sincronizacao-diaria", havingValue = "true")
class SincronizacaoAgendada {

	private static final Logger log = LoggerFactory.getLogger(SincronizacaoAgendada.class);

	private final ImportacaoService servico;
	private final UsuarioService usuarios;
	private final long contaId;

	SincronizacaoAgendada(ImportacaoService servico, UsuarioService usuarios, @Value("${app.importacao.conta-usuario-id:0}") long contaId) {
		this.servico = servico;
		this.usuarios = usuarios;
		this.contaId = contaId;
	}

	@Scheduled(cron = "${app.importacao.cron:0 30 6 * * *}", zone = "America/Sao_Paulo")
	void diaria() {
		if (contaId <= 0) {
			log.warn("Sincronizacao diaria ligada, mas app.importacao.conta-usuario-id nao foi informado");
			return;
		}
		UsuarioDto u = usuarios.buscar(contaId);
		var resultados = servico.sincronizar(new UsuarioLogado(u.id(), u.nome(), u.email(), "x", u.perfil(), u.ativo()));
		resultados.forEach(r -> log.info("Sincronizacao {}: {}", r.fonte(), r.situacao()));
	}
}
