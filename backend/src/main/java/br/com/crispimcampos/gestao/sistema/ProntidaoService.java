package br.com.crispimcampos.gestao.sistema;

import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diagnostico do que ainda falta configurar para o sistema cumprir suas tarefas (captura, alertas, relatorios, acesso).
 * Cada item aponta onde resolver; nada aqui altera dados.
 */
@Service
public class ProntidaoService {

	public enum Situacao { OK, ATENCAO, PENDENTE }

	public record Item(String id, String titulo, Situacao situacao, String detalhe, String rota) {
	}

	private final UsuarioService usuarios;
	private final ConfiguracaoService configuracao;
	private final JdbcTemplate jdbc;
	private final Environment ambiente;

	ProntidaoService(UsuarioService usuarios, ConfiguracaoService configuracao, JdbcTemplate jdbc, Environment ambiente) {
		this.usuarios = usuarios;
		this.configuracao = configuracao;
		this.jdbc = jdbc;
		this.ambiente = ambiente;
	}

	@Transactional(readOnly = true)
	public List<Item> diagnosticar() {
		List<Item> itens = new ArrayList<>();
		List<UsuarioDto> ativos = usuarios.listar().stream().filter(UsuarioDto::ativo).toList();

		var cfg = configuracao.todas();
		boolean timbre = !cfg.get(ConfiguracaoService.ESCRITORIO_ENDERECO).isBlank() && !cfg.get(ConfiguracaoService.ESCRITORIO_CONTATO).isBlank();
		itens.add(new Item("escritorio", "Dados do escritório (timbre dos PDFs)", timbre ? Situacao.OK : Situacao.PENDENTE,
				timbre ? "Endereço e contato preenchidos." : "Preencha endereço e contato para os relatórios saírem completos.", "/sistema"));

		List<String> semOab = ativos.stream().filter(u -> !u.mestre() && (u.perfil() == Perfil.SOCIA || u.perfil() == Perfil.ADVOGADO))
				.filter(u -> u.oabs().stream().noneMatch(o -> o.ativa())).map(UsuarioDto::nome).toList();
		itens.add(new Item("oabs", "OAB de cada sócia e advogado(a)", semOab.isEmpty() ? Situacao.OK : Situacao.PENDENTE,
				semOab.isEmpty() ? "Todas com OAB ativa: a captura de publicações sabe o que monitorar."
						: "Sem OAB ativa (a captura não monitora): " + String.join(", ", semOab) + ".", "/usuarios"));

		List<String> semSupervisor = ativos.stream().filter(u -> u.perfil() == Perfil.ESTAGIARIO && u.supervisorId() == null)
				.map(UsuarioDto::nome).toList();
		itens.add(new Item("supervisores", "Supervisor de cada estagiário(a)", semSupervisor.isEmpty() ? Situacao.OK : Situacao.PENDENTE,
				semSupervisor.isEmpty() ? "Todos os estagiários têm supervisor." : "Sem supervisor: " + String.join(", ", semSupervisor) + ".",
				"/usuarios"));

		long semAlerta = ativos.stream().filter(u -> !u.mestre() && u.destinoAlertas() == null).count();
		itens.add(new Item("alertas-destino", "Destino dos alertas por e-mail", semAlerta == 0 ? Situacao.OK : Situacao.ATENCAO,
				semAlerta == 0 ? "Todas as pessoas recebem alertas." : semAlerta + " pessoa(s) desligaram os alertas por e-mail.", "/usuarios"));

		boolean smtp = !ambiente.getProperty("spring.mail.host", "").isBlank();
		itens.add(new Item("smtp", "Envio de e-mail (SMTP)", smtp ? Situacao.OK : Situacao.PENDENTE,
				smtp ? "Servidor de e-mail configurado." : "Sem SMTP no servidor: alertas ficam pendentes e relatórios não são enviados por e-mail (ver docs/08).",
				null));

		boolean captura = ambiente.getProperty("app.captura.agendada", Boolean.class, false);
		boolean alertas = ambiente.getProperty("app.alertas.agendado", Boolean.class, false);
		itens.add(new Item("rotinas", "Rotinas diárias (captura 06h e alertas)", captura && alertas ? Situacao.OK : Situacao.PENDENTE,
				(captura ? "Captura diária ligada. " : "Captura diária desligada. ") + (alertas ? "Alertas diários ligados." : "Alertas diários desligados."), null));

		Integer filtros = jdbc.queryForObject("select count(*) from filtro_captura where ativo", Integer.class);
		itens.add(new Item("filtros", "Filtros de captura por tribunal", Situacao.OK,
				filtros == null || filtros == 0 ? "Sem filtros: todas as publicações das OABs entram (ajuste em Captura se precisar)."
						: filtros + " filtro(s) ativo(s).", "/captura"));

		boolean google = !ambiente.getProperty("app.google.client-id", "").isBlank();
		itens.add(new Item("google", "Conexão Google (captura por e-mail)", google ? Situacao.OK : Situacao.ATENCAO,
				google ? "Disponível para conectar em Segurança." : "Opcional: exige projeto no Google Cloud (docs/13).", null));

		long sociasSem2fa = jdbc.queryForObject("select count(*) from usuario where ativo and perfil = 'SOCIA' and not totp_ativo", Long.class);
		itens.add(new Item("2fa", "Verificação em duas etapas das sócias", sociasSem2fa == 0 ? Situacao.OK : Situacao.ATENCAO,
				sociasSem2fa == 0 ? "Todas as sócias usam 2FA." : sociasSem2fa + " sócia(s) sem 2FA; recomendado ativar em Segurança.", "/seguranca"));
		return itens;
	}
}
