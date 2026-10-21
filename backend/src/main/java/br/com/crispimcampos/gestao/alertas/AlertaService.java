package br.com.crispimcampos.gestao.alertas;

import br.com.crispimcampos.gestao.agenda.AgendaService;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import br.com.crispimcampos.gestao.financeiro.PainelFinanceiroService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Gera e envia os alertas do item 9 (e-mail; WhatsApp depende de provedor, pendencia 13). Geracao idempotente
 * (restricao unica no banco): rodar duas vezes no dia nao duplica. Envio com tentativas; sem SMTP as notificacoes
 * ficam PENDENTE, e so as dos ultimos {@link #DIAS_ENVIO} dias sao enviadas quando o SMTP for configurado.
 */
@Service
public class AlertaService {

	private static final Logger log = LoggerFactory.getLogger(AlertaService.class);
	static final int DIAS_ENVIO = 1;
	static final int MAXIMO_TENTATIVAS = 3;

	public record Resultado(int geradas, int enviadas, int falhas, int pendentesSemProvedor) {
	}

	private final AgendaService agenda;
	private final UsuarioService usuarios;
	private final CadastroService cadastros;
	private final PainelFinanceiroService financeiro;
	private final AuditoriaService auditoria;
	private final JdbcTemplate jdbc;
	private final Optional<EnviadorEmail> email;
	private final Clock relogio;

	AlertaService(AgendaService agenda, UsuarioService usuarios, CadastroService cadastros, PainelFinanceiroService financeiro,
			AuditoriaService auditoria, JdbcTemplate jdbc, List<EnviadorEmail> enviadores, Clock relogio) {
		this.agenda = agenda;
		this.usuarios = usuarios;
		this.cadastros = cadastros;
		this.financeiro = financeiro;
		this.auditoria = auditoria;
		this.jdbc = jdbc;
		this.email = enviadores.stream().findFirst();
		this.relogio = relogio;
	}

	public Resultado executar(Long atorId) {
		LocalDate hoje = LocalDate.now(relogio);
		List<UsuarioDto> ativos = usuarios.listar().stream().filter(UsuarioDto::ativo).toList();
		Set<Long> socias = ativos.stream().filter(u -> u.perfil() == Perfil.SOCIA).map(UsuarioDto::id).collect(Collectors.toSet());
		Map<Long, String> numeros = new HashMap<>();
		List<NovaNotificacao> novas = new java.util.ArrayList<>(GeradorAlertas.gerar(agenda.abertosParaAlertas(), hoje, socias,
				id -> numeros.computeIfAbsent(id, cadastros::numeroFormatado)));
		var vencidas = financeiro.resumoVencidas(hoje);
		if (vencidas.quantidade() > 0) { // sem nomes de clientes na mensagem (como os demais alertas); detalhes no painel financeiro
			for (Long socia : socias) {
				novas.add(new NovaNotificacao(TipoAlerta.PARCELA_VENCIDA, 0, socia, "Parcelas vencidas",
						"Ha " + vencidas.quantidade() + " parcela(s) de honorarios vencida(s), somando R$ " + vencidas.total().toPlainString()
								+ " em aberto. Veja o painel financeiro."));
			}
		}
		Set<Long> idsAtivos = ativos.stream().map(UsuarioDto::id).collect(Collectors.toSet());
		Set<Long> semAlertaPorEmail = ativos.stream().filter(u -> !u.alertasEmail()).map(UsuarioDto::id).collect(Collectors.toSet());
		int geradas = 0;
		for (NovaNotificacao n : novas) {
			if (!idsAtivos.contains(n.destinatarioId()) || semAlertaPorEmail.contains(n.destinatarioId())) {
				continue;
			}
			geradas += jdbc.update("insert into notificacao (tipo, escopo_id, destinatario_id, canal, data_ref, assunto, corpo) "
					+ "values (?, ?, ?, 'EMAIL', ?, ?, ?) on conflict do nothing", n.tipo().name(), n.escopoId(), n.destinatarioId(),
					java.sql.Date.valueOf(hoje), n.assunto(), n.corpo());
		}
		Resultado r = despachar(hoje, ativos, geradas);
		auditoria.registrar(atorId, "ALERTAS_EXECUTADOS", "alertas", null, "geradas=" + r.geradas() + " enviadas=" + r.enviadas());
		return r;
	}

	private Resultado despachar(LocalDate hoje, List<UsuarioDto> ativos, int geradas) {
		Map<Long, String> emails = new HashMap<>();
		ativos.forEach(u -> {
			if (u.destinoAlertas() != null) { // quem desligou os alertas por e-mail nao recebe
				emails.put(u.id(), u.destinoAlertas());
			}
		});
		var pendentes = jdbc.queryForList("select id, destinatario_id, assunto, corpo, tentativas from notificacao "
				+ "where canal = 'EMAIL' and status in ('PENDENTE') and data_ref >= ? and tentativas < ? order by id",
				java.sql.Date.valueOf(hoje.minusDays(DIAS_ENVIO)), MAXIMO_TENTATIVAS);
		if (email.isEmpty()) {
			if (!pendentes.isEmpty()) {
				log.warn("{} notificacao(oes) pendente(s): configure o SMTP (spring.mail.host) para enviar", pendentes.size());
			}
			return new Resultado(geradas, 0, 0, pendentes.size());
		}
		int enviadas = 0;
		int falhas = 0;
		for (Map<String, Object> p : pendentes) {
			long id = ((Number) p.get("id")).longValue();
			String para = emails.get(((Number) p.get("destinatario_id")).longValue());
			try {
				if (para == null) {
					throw new IllegalStateException("destinatario sem e-mail ativo");
				}
				email.get().enviar(para, (String) p.get("assunto"), (String) p.get("corpo"));
				jdbc.update("update notificacao set status = 'ENVIADA', enviado_em = now(), tentativas = tentativas + 1, erro = null where id = ?", id);
				enviadas++;
			} catch (RuntimeException e) {
				int tentativas = ((Number) p.get("tentativas")).intValue() + 1;
				jdbc.update("update notificacao set tentativas = ?, status = ?, erro = ? where id = ?", tentativas,
						tentativas >= MAXIMO_TENTATIVAS ? "FALHA" : "PENDENTE", e.getClass().getSimpleName(), id);
				falhas++;
				log.warn("Falha ao enviar notificacao {}: {}", id, e.getClass().getSimpleName());
			}
		}
		return new Resultado(geradas, enviadas, falhas, 0);
	}

	/** Socia ve todas; os demais, so as suas. */
	public List<NotificacaoDto> listar(UsuarioLogado u) {
		String sql = "select id, tipo, destinatario_id, canal, data_ref, assunto, corpo, status, tentativas, erro, enviado_em from notificacao "
				+ (u.perfil() == Perfil.SOCIA ? "" : "where destinatario_id = ? ") + "order by id desc limit 300";
		Object[] args = u.perfil() == Perfil.SOCIA ? new Object[0] : new Object[] {u.id()};
		return jdbc.query(sql, (rs, n) -> new NotificacaoDto(rs.getLong(1), TipoAlerta.valueOf(rs.getString(2)), rs.getLong(3),
				rs.getString(4), rs.getDate(5).toLocalDate(), rs.getString(6), rs.getString(7), rs.getString(8), rs.getInt(9),
				rs.getString(10), rs.getTimestamp(11) == null ? null : rs.getTimestamp(11).toInstant()), args);
	}
}
