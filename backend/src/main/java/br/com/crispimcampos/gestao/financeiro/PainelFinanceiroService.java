package br.com.crispimcampos.gestao.financeiro;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.financeiro.PainelFinanceiro.MesFinanceiro;
import br.com.crispimcampos.gestao.financeiro.PainelFinanceiro.ParcelaLinha;
import br.com.crispimcampos.gestao.financeiro.PainelFinanceiro.Periodo;
import br.com.crispimcampos.gestao.financeiro.PainelFinanceiro.ReceitaArea;
import br.com.crispimcampos.gestao.financeiro.PainelFinanceiro.ResumoVencidas;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Painel financeiro (somente socias, em toda operacao; invariante 1). A consulta fica na trilha de auditoria. */
@Service
public class PainelFinanceiroService {

	private static final String BASE = "from parcela p join contrato_honorarios c on c.id = p.contrato_id join cliente cl on cl.id = c.cliente_id "
			+ "where c.ativo and p.situacao <> 'CANCELADA' ";

	private final JdbcTemplate jdbc;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	PainelFinanceiroService(JdbcTemplate jdbc, AuditoriaService auditoria, Clock relogio) {
		this.jdbc = jdbc;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/** Nao e readOnly: a consulta grava a trilha de acesso. */
	@Transactional
	public PainelFinanceiro painel(UsuarioLogado ator, AreaAtuacao area) {
		FinanceiroService.exigirSocia(ator);
		auditoria.registrar(ator.id(), "FINANCEIRO_PAINEL_CONSULTADO", "financeiro", null, area == null ? null : area.name());
		LocalDate hoje = LocalDate.now(relogio);
		String filtro = area == null ? "" : "and c.area = '" + area.name() + "' "; // valor vem do enum, nunca do usuario
		return new PainelFinanceiro(meses(filtro), quantidade(filtro, hoje), total(filtro, hoje), linhas(filtro, "and p.vencimento < ? and p.situacao in ('ABERTA','PARCIAL') ",
				"order by p.vencimento, cl.nome", hoje, hoje), periodo(filtro, hoje, 7), periodo(filtro, hoje, 30), porArea(filtro));
	}

	/** Alerta diario: quantas parcelas estao vencidas e quanto falta receber. */
	@Transactional(readOnly = true)
	public ResumoVencidas resumoVencidas(LocalDate hoje) {
		var m = jdbc.queryForMap("select count(*) as qtd, coalesce(sum(p.valor - coalesce(p.valor_pago, 0)), 0) as total " + BASE
				+ "and p.situacao in ('ABERTA','PARCIAL') and p.vencimento < ?", Date.valueOf(hoje));
		return new ResumoVencidas(((Number) m.get("qtd")).longValue(), (BigDecimal) m.get("total"));
	}

	private List<MesFinanceiro> meses(String filtro) {
		Map<String, BigDecimal[]> m = new TreeMap<>();
		jdbc.query("select to_char(coalesce(p.pago_em, p.vencimento), 'YYYY-MM') as mes, sum(p.valor_pago) as v " + BASE
				+ "and p.situacao in ('PAGA','PARCIAL') " + filtro + "group by 1", rs -> {
					m.computeIfAbsent(rs.getString("mes"), k -> zeros())[0] = rs.getBigDecimal("v");
				});
		jdbc.query("select to_char(p.vencimento, 'YYYY-MM') as mes, sum(p.valor - coalesce(p.valor_pago, 0)) as v " + BASE
				+ "and p.situacao in ('ABERTA','PARCIAL') " + filtro + "group by 1", rs -> {
					m.computeIfAbsent(rs.getString("mes"), k -> zeros())[1] = rs.getBigDecimal("v");
				});
		return m.entrySet().stream().map(e -> new MesFinanceiro(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
	}

	private static BigDecimal[] zeros() {
		return new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO};
	}

	private long quantidade(String filtro, LocalDate hoje) {
		return jdbc.queryForObject("select count(*) " + BASE + "and p.situacao in ('ABERTA','PARCIAL') and p.vencimento < ? " + filtro, Long.class, Date.valueOf(hoje));
	}

	private BigDecimal total(String filtro, LocalDate hoje) {
		return jdbc.queryForObject("select coalesce(sum(p.valor - coalesce(p.valor_pago, 0)), 0) " + BASE
				+ "and p.situacao in ('ABERTA','PARCIAL') and p.vencimento < ? " + filtro, BigDecimal.class, Date.valueOf(hoje));
	}

	private Periodo periodo(String filtro, LocalDate hoje, int dias) {
		List<ParcelaLinha> l = linhas(filtro, "and p.vencimento >= ? and p.vencimento <= ? and p.situacao in ('ABERTA','PARCIAL') ", "order by p.vencimento, cl.nome",
				hoje, hoje, hoje.plusDays(dias));
		return new Periodo(l.size(), l.stream().map(ParcelaLinha::valorEmAberto).reduce(BigDecimal.ZERO, BigDecimal::add), l);
	}

	private List<ParcelaLinha> linhas(String filtro, String condicao, String ordem, LocalDate hoje, Object... args) {
		Object[] a = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			a[i] = args[i] instanceof LocalDate d ? Date.valueOf(d) : args[i];
		}
		List<ParcelaLinha> r = new ArrayList<>();
		jdbc.query("select p.id, p.contrato_id, c.cliente_id, cl.nome as cliente, c.descricao, c.area, p.rotulo, p.numero, p.vencimento, "
				+ "p.valor - coalesce(p.valor_pago, 0) as aberto " + BASE + condicao + filtro + ordem + " limit 500", rs -> {
					LocalDate venc = rs.getDate("vencimento").toLocalDate();
					String rotulo = rs.getString("rotulo") != null ? rs.getString("rotulo") : "Parcela " + rs.getInt("numero");
					r.add(new ParcelaLinha(rs.getLong("id"), rs.getLong("contrato_id"), rs.getLong("cliente_id"), rs.getString("cliente"), rs.getString("descricao"),
							rs.getString("area"), rotulo, venc, rs.getBigDecimal("aberto"), Math.max(0, ChronoUnit.DAYS.between(venc, hoje))));
				}, a);
		return r;
	}

	private List<ReceitaArea> porArea(String filtro) {
		return jdbc.query("select c.area, coalesce(sum(p.valor_pago), 0) as recebido, "
				+ "coalesce(sum(case when p.situacao in ('ABERTA','PARCIAL') then p.valor - coalesce(p.valor_pago, 0) else 0 end), 0) as aberto " + BASE
				+ filtro + "group by c.area order by recebido desc", (rs, n) -> new ReceitaArea(rs.getString("area"), rs.getBigDecimal("recebido"), rs.getBigDecimal("aberto")));
	}
}
