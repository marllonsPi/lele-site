package br.com.crispimcampos.gestao.relatorios;

import br.com.crispimcampos.gestao.agenda.DadosParaCliente;
import br.com.crispimcampos.gestao.alertas.EnviadorEmail;
import br.com.crispimcampos.gestao.atendimento.AndamentoDto;
import br.com.crispimcampos.gestao.atendimento.AtendimentoService;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.cadastros.ProcessoDto;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.financeiro.FinanceiroService;
import java.sql.Array;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Relatorio resumido ao cliente (item 14.2). Fluxo: rascunho gerado pelo sistema -> advogado responsavel ou socia revisa e
 * edita -> aprova -> so entao PDF e envio. Aprovacao e exigida no servico E por CHECK no banco (invariante 6). O conteudo
 * nasce apenas de {@link ConteudoCliente} (lista branca) e a edicao nao pode incluir processo fora do relatorio.
 * Ao enviar, guarda copia, aprovador e data, e marca como repassados os andamentos incluidos.
 */
@Service
public class RelatorioClienteService {

	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final CadastroService cadastros;
	private final DadosParaCliente dadosAgenda;
	private final AtendimentoService atendimentos;
	private final FinanceiroService financeiro;
	private final PdfRenderer pdf;
	private final Timbres timbres;
	private final List<EnviadorEmail> email;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	RelatorioClienteService(JdbcTemplate jdbc, JsonMapper json, CadastroService cadastros, DadosParaCliente dadosAgenda,
			AtendimentoService atendimentos, FinanceiroService financeiro, PdfRenderer pdf, Timbres timbres,
			List<EnviadorEmail> email, AuditoriaService auditoria, Clock relogio) {
		this.jdbc = jdbc;
		this.json = json;
		this.cadastros = cadastros;
		this.dadosAgenda = dadosAgenda;
		this.atendimentos = atendimentos;
		this.financeiro = financeiro;
		this.pdf = pdf;
		this.timbres = timbres;
		this.email = email;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	private record Linha(Long id, Long clienteId, LocalDate ini, LocalDate fim, String estado, boolean fin, String conteudo,
			Long criadoPor, java.time.Instant criadoEm, Long aprovadoPor, java.time.Instant aprovadoEm, Long enviadoPor,
			java.time.Instant enviadoEm, String canal) {
	}

	private final RowMapper<Linha> linha = (rs, n) -> new Linha(rs.getLong("id"), rs.getLong("cliente_id"),
			rs.getDate("periodo_inicio") == null ? null : rs.getDate("periodo_inicio").toLocalDate(), rs.getDate("periodo_fim").toLocalDate(),
			rs.getString("estado"), rs.getBoolean("incluir_financeiro"), rs.getString("conteudo"), rs.getLong("criado_por"),
			rs.getTimestamp("criado_em").toInstant(), (Long) rs.getObject("aprovado_por"),
			rs.getTimestamp("aprovado_em") == null ? null : rs.getTimestamp("aprovado_em").toInstant(), (Long) rs.getObject("enviado_por"),
			rs.getTimestamp("enviado_em") == null ? null : rs.getTimestamp("enviado_em").toInstant(), rs.getString("canal"));

	// ---- rascunho

	@Transactional
	public RelatorioClienteDto criarRascunho(UsuarioLogado ator, Long clienteId, List<Long> processoIds, LocalDate de, LocalDate ate,
			boolean desdeUltimoRelatorio, boolean incluirFinanceiro) {
		exigirPerfil(ator);
		if (incluirFinanceiro && ator.perfil() != Perfil.SOCIA) {
			throw new SecurityException("O resumo financeiro so pode ser incluido por socias");
		}
		var cliente = cadastros.buscarCliente(ator, clienteId);
		List<ProcessoDto> permitidos = cadastros.listarProcessos(ator, clienteId).stream().filter(ProcessoDto::ativo)
				.filter(p -> ator.perfil() == Perfil.SOCIA || ator.id().equals(p.advogadoId())).toList();
		List<ProcessoDto> escolhidos = permitidos;
		if (processoIds != null && !processoIds.isEmpty()) {
			Set<Long> pedidos = new HashSet<>(processoIds);
			escolhidos = permitidos.stream().filter(p -> pedidos.contains(p.id())).toList();
			if (escolhidos.size() != pedidos.size()) {
				throw new IllegalArgumentException("Algum processo informado nao esta disponivel para este relatorio");
			}
		}
		if (escolhidos.isEmpty()) {
			throw new IllegalArgumentException("Nenhum processo disponivel para este relatorio");
		}
		LocalDate hoje = LocalDate.now(relogio);
		LocalDate fim = ate == null ? hoje : ate;
		LocalDate inicio = de;
		if (desdeUltimoRelatorio) {
			inicio = jdbc.query("select max(periodo_fim) from relatorio_cliente where cliente_id = ? and estado = 'ENVIADO'",
					rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate().plusDays(1) : null, clienteId);
		}
		if (inicio != null && inicio.isAfter(fim)) {
			throw new IllegalArgumentException("O periodo inicial nao pode ser posterior ao final");
		}

		List<ConteudoCliente.ProcessoRel> processos = new ArrayList<>();
		List<Long> andamentoIds = new ArrayList<>();
		for (ProcessoDto p : escolhidos) {
			List<AndamentoDto> andamentos = atendimentos.andamentosDoProcesso(p.id(), inicio, fim);
			andamentos.forEach(a -> andamentoIds.add(a.id()));
			processos.add(new ConteudoCliente.ProcessoRel(p.numeroFormatado(), p.varaComarca(), p.tribunal(), p.classe(), p.situacao(),
					p.partes().stream().map(x -> x.nome()).toList(),
					andamentos.stream().map(a -> new ConteudoCliente.Item(a.data(), a.descricao())).toList(),
					dadosAgenda.feitos(p.id(), inicio, fim).stream().map(f -> new ConteudoCliente.Item(f.data(), Linguagem.feito(f.tipo()))).toList(),
					dadosAgenda.proximosPassos(p.id(), hoje).stream().map(this::proximo).toList()));
		}
		ConteudoCliente.FinanceiroRel fin = null;
		if (incluirFinanceiro) {
			var r = financeiro.resumoDoCliente(ator, clienteId);
			fin = new ConteudoCliente.FinanceiroRel(r.parcelasPagas(), r.parcelasAVencer().add(r.parcelasEmAtraso()));
		}
		ConteudoCliente conteudo = new ConteudoCliente(cliente.nome(), inicio, fim, processos, null, fin);
		Long id = jdbc.queryForObject("insert into relatorio_cliente (cliente_id, periodo_inicio, periodo_fim, incluir_financeiro, conteudo, andamento_ids, criado_por) "
				+ "values (?, ?, ?, ?, ?, ?, ?) returning id", Long.class, clienteId, inicio == null ? null : java.sql.Date.valueOf(inicio),
				java.sql.Date.valueOf(fim), incluirFinanceiro, json.writeValueAsString(conteudo),
				andamentoIds.toArray(new Long[0]), ator.id());
		auditoria.registrar(ator.id(), "RELATORIO_CLIENTE_RASCUNHO", "relatorio_cliente", String.valueOf(id), null);
		return dto(buscarLinha(id));
	}

	private ConteudoCliente.Item proximo(DadosParaCliente.ProximoPasso p) {
		if (p.hora() != null) {
			String onde = p.localOuLink() == null ? "" : " - " + p.localOuLink();
			return new ConteudoCliente.Item(p.data(), "Audiencia marcada para as " + p.hora().toString().substring(0, 5) + " (" + p.modalidade().name().toLowerCase() + ")" + onde);
		}
		return new ConteudoCliente.Item(p.data(), Linguagem.providencia(p.tipo()) + " (prazo legal ate esta data)");
	}

	// ---- revisao, aprovacao

	@Transactional(readOnly = true)
	public RelatorioClienteDto buscar(UsuarioLogado ator, Long id) {
		return dto(acessivel(ator, id));
	}

	@Transactional(readOnly = true)
	public List<RelatorioClienteDto> doCliente(UsuarioLogado ator, Long clienteId) {
		exigirPerfil(ator);
		cadastros.buscarCliente(ator, clienteId);
		return jdbc.query("select * from relatorio_cliente where cliente_id = ? order by id desc", linha, clienteId).stream()
				.filter(l -> ator.perfil() == Perfil.SOCIA || ator.id().equals(l.criadoPor())).map(this::dto).toList();
	}

	@Transactional
	public RelatorioClienteDto editar(UsuarioLogado ator, Long id, ConteudoCliente novo) {
		Linha l = acessivel(ator, id);
		if (!List.of("RASCUNHO", "EM_REVISAO").contains(l.estado())) {
			throw new IllegalStateException("O relatorio ja foi aprovado e nao pode ser alterado");
		}
		ConteudoCliente atual = ler(l);
		Set<String> permitidos = atual.processos().stream().map(ConteudoCliente.ProcessoRel::numero).collect(Collectors.toSet());
		if (novo.processos() == null || novo.processos().isEmpty()
				|| !novo.processos().stream().allMatch(p -> permitidos.contains(p.numero()))) {
			throw new IllegalArgumentException("O relatorio so pode conter os processos incluidos na geracao");
		}
		if (novo.financeiro() != null && atual.financeiro() == null) {
			throw new IllegalArgumentException("O resumo financeiro nao foi incluido na geracao");
		}
		ConteudoCliente seguro = new ConteudoCliente(atual.clienteNome(), atual.periodoInicio(), atual.periodoFim(), novo.processos(),
				novo.comentarios(), novo.financeiro() == null ? null : atual.financeiro());
		jdbc.update("update relatorio_cliente set conteudo = ?, estado = 'EM_REVISAO' where id = ?", json.writeValueAsString(seguro), id);
		auditoria.registrar(ator.id(), "RELATORIO_CLIENTE_EDITADO", "relatorio_cliente", String.valueOf(id), null);
		return dto(buscarLinha(id));
	}

	@Transactional
	public RelatorioClienteDto aprovar(UsuarioLogado ator, Long id) {
		Linha l = acessivel(ator, id);
		if (!List.of("RASCUNHO", "EM_REVISAO").contains(l.estado())) {
			throw new IllegalStateException("O relatorio ja foi aprovado");
		}
		jdbc.update("update relatorio_cliente set estado = 'APROVADO', aprovado_por = ?, aprovado_em = now() where id = ?", ator.id(), id);
		auditoria.registrar(ator.id(), "RELATORIO_CLIENTE_APROVADO", "relatorio_cliente", String.valueOf(id), null);
		return dto(buscarLinha(id));
	}

	// ---- saida (so apos aprovacao)

	@Transactional(readOnly = true)
	public byte[] gerarPdf(UsuarioLogado ator, Long id) {
		Linha l = acessivel(ator, id);
		return pdfAprovado(l);
	}

	@Transactional
	public RelatorioClienteDto enviar(UsuarioLogado ator, Long id, String canal) {
		Linha l = acessivel(ator, id);
		if (!"APROVADO".equals(l.estado())) {
			throw new IllegalStateException("Somente relatorio aprovado pode ser enviado");
		}
		if (!List.of("EMAIL", "WHATSAPP", "IMPRESSAO").contains(canal)) {
			throw new IllegalArgumentException("Canal invalido (EMAIL, WHATSAPP ou IMPRESSAO)");
		}
		byte[] arquivo = pdfAprovado(l);
		if ("EMAIL".equals(canal)) {
			String para = cadastros.buscarCliente(ator, l.clienteId()).email();
			if (para == null || para.isBlank()) {
				throw new IllegalArgumentException("O cliente nao tem e-mail cadastrado");
			}
			if (email.isEmpty()) {
				throw new IllegalStateException("Envio por e-mail nao configurado (SMTP)");
			}
			email.get(0).enviarComAnexo(para, "Relatorio de andamento processual",
					"Prezado(a) cliente, segue em anexo o relatorio de andamento do seu caso. Em caso de duvidas, fale conosco.",
					"relatorio-" + id + ".pdf", arquivo);
		}
		// WHATSAPP e IMPRESSAO: o escritorio entrega o PDF por conta propria (sem provedor integrado, pendencia 13); fica registrado
		jdbc.update("update relatorio_cliente set estado = 'ENVIADO', enviado_por = ?, enviado_em = now(), canal = ?, pdf = ? where id = ?",
				ator.id(), canal, arquivo, id);
		Long[] ids = jdbc.queryForObject("select andamento_ids from relatorio_cliente where id = ?", (rs, n) -> {
			Array a = rs.getArray(1);
			return a == null ? new Long[0] : (Long[]) a.getArray();
		}, id);
		int marcados = atendimentos.marcarRepassadosPorRelatorio(List.of(ids), canal, ator.id());
		auditoria.registrar(ator.id(), "RELATORIO_CLIENTE_ENVIADO", "relatorio_cliente", String.valueOf(id), "canal=" + canal + " andamentos=" + marcados);
		return dto(buscarLinha(id));
	}

	// ---- internos

	private byte[] pdfAprovado(Linha l) {
		if (!List.of("APROVADO", "ENVIADO").contains(l.estado())) {
			throw new IllegalStateException("O relatorio precisa ser aprovado antes de gerar o PDF");
		}
		if ("ENVIADO".equals(l.estado())) {
			return jdbc.queryForObject("select pdf from relatorio_cliente where id = ?", byte[].class, l.id());
		}
		return pdf.renderizar(HtmlRelatorios.cliente(timbres.timbre(), ler(l)));
	}

	private void exigirPerfil(UsuarioLogado ator) {
		if (ator.perfil() != Perfil.SOCIA && ator.perfil() != Perfil.ADVOGADO) {
			throw new SecurityException("Somente socias e o advogado responsavel geram relatorio ao cliente");
		}
	}

	/** Socia ve todos; advogado so os que ele mesmo gerou (processos proprios). Fora disso, inexistente. */
	private Linha acessivel(UsuarioLogado ator, Long id) {
		exigirPerfil(ator);
		Linha l = jdbc.query("select * from relatorio_cliente where id = ?", linha, id).stream().findFirst()
				.orElseThrow(() -> new RelatorioNaoEncontradoException("Relatorio nao encontrado"));
		if (ator.perfil() != Perfil.SOCIA && !ator.id().equals(l.criadoPor())) {
			throw new RelatorioNaoEncontradoException("Relatorio nao encontrado");
		}
		return l;
	}

	private Linha buscarLinha(Long id) {
		return jdbc.queryForObject("select * from relatorio_cliente where id = ?", linha, id);
	}

	private ConteudoCliente ler(Linha l) {
		return json.readValue(l.conteudo(), ConteudoCliente.class);
	}

	private RelatorioClienteDto dto(Linha l) {
		return new RelatorioClienteDto(l.id(), l.clienteId(), l.ini(), l.fim(), l.estado(), l.fin(), ler(l), l.criadoPor(), l.criadoEm(),
				l.aprovadoPor(), l.aprovadoEm(), l.enviadoPor(), l.enviadoEm(), l.canal());
	}
}
