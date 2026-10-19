package br.com.crispimcampos.gestao.relatorios;

import br.com.crispimcampos.gestao.agenda.AgendaItemDto;
import br.com.crispimcampos.gestao.agenda.AgendaService;
import br.com.crispimcampos.gestao.alertas.EnviadorEmail;
import br.com.crispimcampos.gestao.atendimento.AtendimentoService;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import br.com.crispimcampos.gestao.financeiro.FinanceiroService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Relatorios internos (item 14.1). Tudo parte de dados que o usuario ja pode ver (Visibilidade); a agenda "geral", a de outra
 * pessoa, a conferencia e o financeiro sao das socias. Exportacao em PDF e envio as socias por e-mail.
 */
@Service
public class RelatoriosInternosService {

	public enum Tipo {
		AGENDA_DIA, AGENDA_USUARIO, PENDENTES, VENCIDOS, FINALIZADOS, ATENDIMENTOS, FINANCEIRO_CLIENTE
	}

	private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter DH = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final AgendaService agenda;
	private final AtendimentoService atendimentos;
	private final FinanceiroService financeiro;
	private final UsuarioService usuarios;
	private final PdfRenderer pdf;
	private final Timbres timbres;
	private final List<EnviadorEmail> email;
	private final AuditoriaService auditoria;
	private final Clock relogio;

	RelatoriosInternosService(AgendaService agenda, AtendimentoService atendimentos, FinanceiroService financeiro,
			UsuarioService usuarios, PdfRenderer pdf, Timbres timbres, List<EnviadorEmail> email, AuditoriaService auditoria, Clock relogio) {
		this.agenda = agenda;
		this.atendimentos = atendimentos;
		this.financeiro = financeiro;
		this.usuarios = usuarios;
		this.pdf = pdf;
		this.timbres = timbres;
		this.email = email;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	public RelatorioDados gerar(UsuarioLogado ator, Tipo tipo, LocalDate de, LocalDate ate, Long usuarioId, Long clienteId) {
		LocalDate hoje = LocalDate.now(relogio);
		boolean socia = ator.perfil() == Perfil.SOCIA;
		Map<Long, String> nomes = usuarios.listar().stream().collect(Collectors.toMap(UsuarioDto::id, UsuarioDto::nome));
		Function<Long, String> nome = id -> id == null ? "-" : nomes.getOrDefault(id, "#" + id);
		LocalDate ini = de == null ? hoje : de;
		LocalDate fim = ate == null ? ini : ate;
		String gerado = java.time.LocalDateTime.now(relogio).format(DH);
		Long alvo = socia ? usuarioId : ator.id();
		List<String> colunas = List.of("Data", "Hora", "Tipo", "Descricao", "Responsavel", "Criado por", "Situacao");
		switch (tipo) {
			case AGENDA_DIA -> {
				var itens = agenda.todosAbertos(ator).stream().filter(i -> i.data().equals(ini))
						.filter(i -> alvo == null ? socia : i.responsavelId().equals(alvo)).toList();
				return new RelatorioDados("Agenda do dia " + ini.format(D), alvo == null ? "Todo o escritorio" : "Agenda de " + nome.apply(alvo),
						colunas, linhas(itens, nome), gerado);
			}
			case AGENDA_USUARIO -> {
				exigirSocia(socia);
				if (usuarioId == null) {
					throw new IllegalArgumentException("Informe o usuario");
				}
				List<List<String>> l = new ArrayList<>(linhas(agenda.todosAbertos(ator).stream().filter(i -> i.responsavelId().equals(usuarioId)).toList(), nome));
				agenda.finalizadosNoPeriodo(ator, ini, fim).stream().filter(f -> f.item().responsavelId().equals(usuarioId))
						.forEach(f -> l.add(linha(f.item(), nome, "Finalizado em " + f.finalizadoEm().atZone(relogio.getZone()).format(DH))));
				return new RelatorioDados("Agenda de " + nome.apply(usuarioId) + " para conferencia", "Periodo " + ini.format(D) + " a " + fim.format(D), colunas, l, gerado);
			}
			case PENDENTES -> {
				var itens = agenda.todosAbertos(ator).stream().filter(i -> socia || i.responsavelId().equals(ator.id())).toList();
				return new RelatorioDados("Itens pendentes", socia ? "Todo o escritorio" : "Seus itens", colunas, linhas(itens, nome), gerado);
			}
			case VENCIDOS -> {
				var itens = agenda.todosAbertos(ator).stream().filter(i -> i.data().isBefore(hoje))
						.filter(i -> socia || i.responsavelId().equals(ator.id())).toList();
				return new RelatorioDados("Itens vencidos e nao finalizados", "Posicao em " + hoje.format(D), colunas, linhas(itens, nome), gerado);
			}
			case FINALIZADOS -> {
				var itens = agenda.finalizadosNoPeriodo(ator, ini, fim).stream()
						.filter(f -> socia || f.item().responsavelId().equals(ator.id())).toList();
				List<List<String>> l = itens.stream().map(f -> linha(f.item(), nome, f.item().status().name() + " - " + f.finalizadoEm().atZone(relogio.getZone()).format(DH))).toList();
				return new RelatorioDados("Finalizados no periodo", ini.format(D) + " a " + fim.format(D), colunas, l, gerado);
			}
			case ATENDIMENTOS -> {
				var lista = atendimentos.listar(ator, clienteId).stream().filter(a -> {
					LocalDate dia = a.momento().atZone(relogio.getZone()).toLocalDate();
					return !dia.isBefore(ini) && !dia.isAfter(fim);
				}).toList();
				List<List<String>> l = lista.stream().map(a -> List.of(a.momento().atZone(relogio.getZone()).format(DH), a.canal(), nome.apply(a.atendenteId()),
						a.origem(), a.resumo())).toList();
				return new RelatorioDados("Atendimentos realizados", ini.format(D) + " a " + fim.format(D),
						List.of("Quando", "Canal", "Atendente", "Origem", "Resumo"), l, gerado);
			}
			case FINANCEIRO_CLIENTE -> {
				exigirSocia(socia);
				if (clienteId == null) {
					throw new IllegalArgumentException("Informe o cliente");
				}
				var r = financeiro.resumoDoCliente(ator, clienteId);
				List<List<String>> l = List.of(List.of("Honorarios contratados", HtmlRelatorios.moeda(r.contratado())),
						List.of("Parcelas pagas", HtmlRelatorios.moeda(r.parcelasPagas())), List.of("Parcelas a vencer", HtmlRelatorios.moeda(r.parcelasAVencer())),
						List.of("Parcelas em atraso (" + r.quantidadeEmAtraso() + ")", HtmlRelatorios.moeda(r.parcelasEmAtraso())),
						List.of("Custas e despesas", HtmlRelatorios.moeda(r.custasEDespesas())), List.of("Despesas reembolsaveis", HtmlRelatorios.moeda(r.reembolsavel())),
						List.of("Exito previsto / recebido", HtmlRelatorios.moeda(r.exitoPrevisto()) + " / " + HtmlRelatorios.moeda(r.exitoRecebido())),
						List.of("Sucumbencia prevista / recebida", HtmlRelatorios.moeda(r.sucumbenciaPrevista()) + " / " + HtmlRelatorios.moeda(r.sucumbenciaRecebida())));
				return new RelatorioDados("Financeiro por cliente", "Cliente #" + clienteId + " - posicao em " + hoje.format(D), List.of("Item", "Valor"), l, gerado);
			}
			default -> throw new IllegalArgumentException("Tipo de relatorio desconhecido");
		}
	}

	public byte[] pdf(UsuarioLogado ator, Tipo tipo, LocalDate de, LocalDate ate, Long usuarioId, Long clienteId) {
		return pdf.renderizar(HtmlRelatorios.interno(timbres.timbre(), gerar(ator, tipo, de, ate, usuarioId, clienteId)));
	}

	/** Envia o PDF as socias ativas (item 14.1). */
	public int enviarAsSocias(UsuarioLogado ator, Tipo tipo, LocalDate de, LocalDate ate, Long usuarioId, Long clienteId) {
		exigirSocia(ator.perfil() == Perfil.SOCIA);
		if (email.isEmpty()) {
			throw new IllegalStateException("Envio por e-mail nao configurado (SMTP)");
		}
		RelatorioDados dados = gerar(ator, tipo, de, ate, usuarioId, clienteId);
		byte[] arquivo = pdf.renderizar(HtmlRelatorios.interno(timbres.timbre(), dados));
		int enviados = 0;
		for (UsuarioDto u : usuarios.listar()) {
			if (u.ativo() && u.perfil() == Perfil.SOCIA) {
				email.get(0).enviarComAnexo(u.email(), dados.titulo(), "Segue em anexo o relatorio interno: " + dados.titulo() + ".",
						"relatorio-" + tipo.name().toLowerCase() + ".pdf", arquivo);
				enviados++;
			}
		}
		auditoria.registrar(ator.id(), "RELATORIO_INTERNO_ENVIADO", "relatorio", tipo.name(), "destinatarios=" + enviados);
		return enviados;
	}

	private static void exigirSocia(boolean socia) {
		if (!socia) {
			throw new SecurityException("Este relatorio e restrito as socias");
		}
	}

	private List<List<String>> linhas(List<AgendaItemDto> itens, Function<Long, String> nome) {
		return itens.stream().map(i -> linha(i, nome, i.status().name().replace('_', ' ').toLowerCase())).toList();
	}

	private List<String> linha(AgendaItemDto i, Function<Long, String> nome, String situacao) {
		return List.of(i.data().format(D), i.hora() == null ? "" : i.hora().toString().substring(0, 5), i.tipo().name() + (i.urgente() ? " (URGENTE)" : ""),
				i.descricao() == null ? "" : i.descricao(), nome.apply(i.responsavelId()), nome.apply(i.criadoPor()), situacao);
	}
}
