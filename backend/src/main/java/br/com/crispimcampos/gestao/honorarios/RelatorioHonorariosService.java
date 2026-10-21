package br.com.crispimcampos.gestao.honorarios;

import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relatorios de honorarios (somente socias): sugerido x fechado por advogado, area e mes; conversao dos atendimentos; ticket medio
 * por area; e os casos fechados abaixo do minimo da OAB, com a justificativa, para conferencia.
 */
@Service
public class RelatorioHonorariosService {

	public record SugeridoXFechado(Long advogadoId, String advogado, String area, String mes, long propostas, BigDecimal sugerido, BigDecimal fechado) {
	}

	/** Reunioes realizadas (atendimentos concluidos) x propostas fechadas no periodo. */
	public record Conversao(Long advogadoId, String advogado, long reunioes, long fechadas, BigDecimal taxa) {
	}

	public record TicketMedio(String area, long contratos, BigDecimal total, BigDecimal ticketMedio) {
	}

	public record AbaixoDaTabela(Long propostaId, String cliente, String advogado, LocalDate data, BigDecimal minimoOab, BigDecimal fechado, BigDecimal diferenca, String tipo, String justificativa) {
	}

	public record Relatorio(LocalDate de, LocalDate ate, List<SugeridoXFechado> sugeridoXFechado, List<Conversao> conversao, Conversao conversaoGeral, List<TicketMedio> ticketMedio,
			List<AbaixoDaTabela> abaixoDaTabela) {
	}

	private final JdbcTemplate jdbc;
	private final AuditoriaService auditoria;

	RelatorioHonorariosService(JdbcTemplate jdbc, AuditoriaService auditoria) {
		this.jdbc = jdbc;
		this.auditoria = auditoria;
	}

	/** Nao e readOnly: a consulta de dado financeiro fica na trilha de auditoria. */
	@Transactional
	public Relatorio gerar(UsuarioLogado u, LocalDate de, LocalDate ate) {
		if (u.perfil() != Perfil.SOCIA) {
			throw new SecurityException("Os relatorios de honorarios sao restritos as socias");
		}
		LocalDate fim = ate == null ? LocalDate.now() : ate;
		LocalDate ini = de == null ? fim.minusMonths(12).withDayOfMonth(1) : de;
		if (ini.isAfter(fim)) {
			throw new IllegalArgumentException("Periodo invalido");
		}
		java.sql.Date d0 = java.sql.Date.valueOf(ini);
		java.sql.Date d1 = java.sql.Date.valueOf(fim.plusDays(1)); // exclusivo
		auditoria.registrar(u.id(), "RELATORIO_HONORARIOS_CONSULTADO", "proposta_honorarios", null, ini + ".." + fim);

		// o valor fechado e repartido entre as areas na proporcao do valor sugerido de cada servico
		String base = "from proposta_honorarios p join usuario a on a.id = p.advogado_id join ("
				+ "select i.proposta_id, i.area, coalesce(i.valor_sugerido, 0) / nullif(sum(coalesce(i.valor_sugerido, 0)) over (partition by i.proposta_id), 0) as parte, "
				+ "1.0 / count(*) over (partition by i.proposta_id) as parte_igual from proposta_item i) x on x.proposta_id = p.id "
				+ "where p.status = 'FECHADA' and p.fechado_em >= ? and p.fechado_em < ? ";
		List<SugeridoXFechado> sxf = jdbc.query("select p.advogado_id, a.nome, x.area, to_char(p.fechado_em, 'YYYY-MM') as mes, count(distinct p.id) as propostas, "
				+ "sum(p.valor_sugerido * coalesce(x.parte, x.parte_igual)) as sugerido, sum(p.valor_fechado * coalesce(x.parte, x.parte_igual)) as fechado " + base
				+ "group by p.advogado_id, a.nome, x.area, mes order by mes, a.nome, x.area", (rs, n) -> new SugeridoXFechado(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getLong(5), rs.getBigDecimal(6).setScale(2, RoundingMode.HALF_UP), rs.getBigDecimal(7).setScale(2, RoundingMode.HALF_UP)), d0, d1);

		List<TicketMedio> ticket = jdbc.query("select x.area, count(distinct p.id) as contratos, sum(p.valor_fechado * coalesce(x.parte, x.parte_igual)) as total " + base + "group by x.area order by total desc",
				(rs, n) -> {
					BigDecimal total = rs.getBigDecimal(3).setScale(2, RoundingMode.HALF_UP);
					long c = rs.getLong(2);
					return new TicketMedio(rs.getString(1), c, total, c == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(c), 2, RoundingMode.HALF_UP));
				}, d0, d1);

		List<Conversao> conversao = jdbc.query("select u.id, u.nome, "
				+ "(select count(*) from atendimento at join agendamento ag on ag.id = at.agendamento_id where at.origem = 'AGENDAMENTO' and ag.advogado_atendente_id = u.id and at.momento >= ? and at.momento < ?) as reunioes, "
				+ "(select count(*) from proposta_honorarios p where p.advogado_id = u.id and p.status = 'FECHADA' and p.fechado_em >= ? and p.fechado_em < ?) as fechadas "
				+ "from usuario u where u.perfil in ('SOCIA', 'ADVOGADO') and u.ativo order by u.nome", (rs, n) -> conv(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4)), d0, d1, d0, d1)
				.stream().filter(c -> c.reunioes() > 0 || c.fechadas() > 0).toList();
		long reunioes = conversao.stream().mapToLong(Conversao::reunioes).sum();
		long fechadas = conversao.stream().mapToLong(Conversao::fechadas).sum();

		List<AbaixoDaTabela> abaixo = jdbc.query("select p.id, c.nome as cliente, a.nome as advogado, p.fechado_em::date as data, p.valor_minimo_oab, p.valor_fechado, p.justificativa_tipo, p.justificativa "
				+ "from proposta_honorarios p join cliente c on c.id = p.cliente_id join usuario a on a.id = p.advogado_id where p.status = 'FECHADA' and p.valor_fechado < p.valor_minimo_oab "
				+ "and p.fechado_em >= ? and p.fechado_em < ? order by p.fechado_em desc", (rs, n) -> new AbaixoDaTabela(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate(),
				rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(5).subtract(rs.getBigDecimal(6)), rs.getString(7), rs.getString(8)), d0, d1);
		return new Relatorio(ini, fim, sxf, conversao, conv(0L, "Todos", reunioes, fechadas), ticket, abaixo);
	}

	private static Conversao conv(Long id, String nome, long reunioes, long fechadas) {
		BigDecimal taxa = reunioes == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(fechadas).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(reunioes), 1, RoundingMode.HALF_UP);
		return new Conversao(id, nome, reunioes, fechadas, taxa);
	}
}
