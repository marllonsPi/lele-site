package br.com.crispimcampos.gestao.dev;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.MatrizVisibilidadeService;
import br.com.crispimcampos.gestao.acesso.ParVisibilidade;
import br.com.crispimcampos.gestao.agenda.CompromissoService;
import br.com.crispimcampos.gestao.agenda.Modalidade;
import br.com.crispimcampos.gestao.agenda.ObservacaoService;
import br.com.crispimcampos.gestao.agenda.PrazoDto;
import br.com.crispimcampos.gestao.agenda.PrazoService;
import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.atendimento.AtendimentoService;
import br.com.crispimcampos.gestao.modelos.ModeloService;
import br.com.crispimcampos.gestao.captura.DadosBrutos;
import br.com.crispimcampos.gestao.captura.Fonte;
import br.com.crispimcampos.gestao.captura.FiltroService;
import br.com.crispimcampos.gestao.captura.PipelineCaptura;
import br.com.crispimcampos.gestao.cadastros.NumeroCnj;
import br.com.crispimcampos.gestao.cadastros.PapelParte;
import br.com.crispimcampos.gestao.cadastros.ProcessoDto;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.financeiro.FinanceiroService;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Equipe e casos ficticios para testar a mao (nomes e numeros inventados; invariante 9). Roda so no perfil dev e
 * so com o banco vazio. A equipe imita a matriz da spec: Socia A e B, Advogada C (como Andressa), Advogado D
 * (como Wendell) e Assistente E (como Luana, ve A, B e C mas nao D).
 */
@Component
@Profile("dev")
public class DevSeed implements ApplicationRunner {

	public static final String SENHA = "senha-de-teste-123";
	private static final Logger log = LoggerFactory.getLogger(DevSeed.class);

	private final UsuarioService usuarios;
	private final MatrizVisibilidadeService matriz;
	private final CadastroService cadastros;
	private final PrazoService prazos;
	private final CompromissoService compromissos;
	private final ObservacaoService observacoes;
	private final PipelineCaptura pipeline;
	private final FiltroService filtros;
	private final AtendimentoService atendimentos;
	private final FinanceiroService financeiro;
	private final ModeloService modelos;

	DevSeed(UsuarioService usuarios, MatrizVisibilidadeService matriz, CadastroService cadastros, PrazoService prazos,
			CompromissoService compromissos, ObservacaoService observacoes, PipelineCaptura pipeline, FiltroService filtros,
			AtendimentoService atendimentos, FinanceiroService financeiro, ModeloService modelos) {
		this.usuarios = usuarios;
		this.matriz = matriz;
		this.cadastros = cadastros;
		this.prazos = prazos;
		this.compromissos = compromissos;
		this.observacoes = observacoes;
		this.pipeline = pipeline;
		this.filtros = filtros;
		this.atendimentos = atendimentos;
		this.financeiro = financeiro;
		this.modelos = modelos;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!usuarios.listar().isEmpty()) {
			return;
		}
		UsuarioDto a = usuarios.criar(null, "Socia A (teste)", "socia.a@teste.local", SENHA, Perfil.SOCIA, null);
		UsuarioDto b = usuarios.criar(null, "Socia B (teste)", "socia.b@teste.local", SENHA, Perfil.SOCIA, null);
		UsuarioDto c = usuarios.criar(null, "Advogada C (teste)", "advogada.c@teste.local", SENHA, Perfil.ADVOGADO, null);
		UsuarioDto d = usuarios.criar(null, "Advogado D (teste)", "advogado.d@teste.local", SENHA, Perfil.ADVOGADO, null);
		UsuarioDto e = usuarios.criar(null, "Assistente E (teste)", "assistente.e@teste.local", SENHA, Perfil.ASSISTENTE, null);
		UsuarioDto f = usuarios.criar(null, "Estagiario F (teste)", "estagiario.f@teste.local", SENHA, Perfil.ESTAGIARIO, c.id());
		usuarios.adicionarOab(a.id(), a.id(), "90001", "DF", true);
		usuarios.adicionarOab(a.id(), a.id(), "90001", "SP", false);
		usuarios.adicionarOab(a.id(), b.id(), "90002", "DF", true);
		usuarios.adicionarOab(a.id(), c.id(), "90003", "DF", true);
		usuarios.adicionarOab(a.id(), d.id(), "90004", "DF", true);
		usuarios.adicionarOab(a.id(), d.id(), "90004", "GO", false);

		// Assistente E: ve A, B e C em todos os escopos; D nao (docs/03)
		Set<ParVisibilidade> linhaE = new HashSet<>();
		for (UsuarioDto titular : new UsuarioDto[] {a, b, c}) {
			for (Escopo escopo : EnumSet.allOf(Escopo.class)) {
				linhaE.add(new ParVisibilidade(e.id(), titular.id(), escopo));
			}
		}
		matriz.definirLinha(a.id(), e.id(), linhaE);

		UsuarioLogado socia = logado(a, Perfil.SOCIA);
		UsuarioLogado socia2 = logado(b, Perfil.SOCIA);
		UsuarioLogado assistente = logado(e, Perfil.ASSISTENTE);

		var maria = cadastros.criarCliente(a.id(), "Maria Ficticia", "529.982.247-25", "maria@exemplo.test", "(61) 90000-0001", "Rua Ficticia 1", null);
		var empresa = cadastros.criarCliente(a.id(), "Empresa Ficticia Ltda", "11.222.333/0001-81", "contato@exemplo.test", null, null, "Cliente corporativo de teste");
		var joao = cadastros.criarCliente(a.id(), "Joao Ficticio", "111.444.777-35", null, "(61) 90000-0003", null, null);

		ProcessoDto pC = processo(a, 1, maria.id(), c.id(), "TJDFT");
		ProcessoDto pD = processo(a, 2, empresa.id(), d.id(), "TRT-10");
		ProcessoDto pA = processo(a, 3, joao.id(), a.id(), "TJDFT");
		processo(a, 4, maria.id(), b.id(), "TJSP");
		cadastros.adicionarParte(a.id(), pC.id(), "Banco Ficticio S.A.", PapelParte.CONTRARIA);

		LocalDate hoje = LocalDate.now();
		try {
			// Lancado pela assistente: aguarda conferencia de outra pessoa
			PrazoDto aguardando = prazos.lancar(assistente, pC.id(), TipoAgendamento.CON, "Contestacao do caso Maria", e.id(), false, hoje.minusDays(5), hoje.plusDays(6));
			// Lancado pela socia A e conferido pela socia B: ja pendente
			PrazoDto conferido = prazos.lancar(socia, pA.id(), TipoAgendamento.MAN, "Manifestacao sobre laudo", a.id(), false, hoje.minusDays(8), hoje.plusDays(2));
			prazos.conferir(socia2, conferido.id());
			// Vencido e urgente
			prazos.lancar(socia, pC.id(), TipoAgendamento.REC, "Recurso de apelacao", c.id(), true, hoje.minusDays(20), hoje.minusDays(1));
			prazos.lancar(socia, pD.id(), TipoAgendamento.INI, "Peticao inicial do caso Empresa", d.id(), false, hoje.minusDays(2), hoje.plusDays(9));
			// Tarefa distribuida ao estagiario F (supervisor: Advogada C), ja conferida: ele finaliza e ela aprova
			PrazoDto doEstagiario = prazos.lancar(socia, pC.id(), TipoAgendamento.MAN, "Juntar procuracao (estagiario)", f.id(), false, hoje.minusDays(3), hoje.plusDays(8));
			prazos.conferir(socia2, doEstagiario.id());
			observacoes.adicionar(socia, aguardando.id(), "Conferir se a contagem considerou o feriado local.");
		} catch (RuntimeException ex) {
			log.warn("Prazos de exemplo nao criados (calendario cobre so 2026-2027?): {}", ex.getMessage());
		}

		LocalDate amanha = hoje.plusDays(1);
		// Duas audiencias sobrepostas de advogados diferentes -> aviso de conflito no escritorio
		compromissos.marcar(socia, TipoAgendamento.AUD, pC.id(), null, "Audiencia de instrucao", c.id(), false, LocalDateTime.of(amanha, LocalTime.of(10, 0)), 60, Modalidade.PRESENCIAL, "Forum - Sala 3");
		compromissos.marcar(socia, TipoAgendamento.AUD, pD.id(), null, "Audiencia de conciliacao", d.id(), false, LocalDateTime.of(amanha, LocalTime.of(10, 30)), 60, Modalidade.ONLINE, "https://exemplo.test/sala-d");
		compromissos.marcar(socia, TipoAgendamento.AUD, pA.id(), null, "Audiencia una", a.id(), false, LocalDateTime.of(hoje.plusDays(8), LocalTime.of(14, 0)), null, Modalidade.ONLINE, "https://exemplo.test/sala-a");
		compromissos.marcar(socia, TipoAgendamento.AUD, pD.id(), null, "Audiencia de julgamento", d.id(), false, LocalDateTime.of(hoje.plusDays(10), LocalTime.of(9, 0)), 90, Modalidade.PRESENCIAL, "Tribunal - Sala 7");
		compromissos.marcar(socia, TipoAgendamento.ATD, null, joao.id(), "Primeira consulta", c.id(), false, LocalDateTime.of(hoje, LocalTime.of(16, 0)), 45, Modalidade.TELEFONE, null);
		// Captura: filtro da Socia A (e-mail de TRT, exceto TRT-10) e publicacoes ficticias para a fila "A classificar"
		filtros.criar(a.id(), a.id(), Fonte.EMAIL, "TRT", "TRT-10");
		String n1 = numeroDoProcesso(1);
		pipeline.processar(c.id(), java.util.List.of(
				new DadosBrutos(Fonte.DJEN, "9001", n1, "TJDFT", hoje, "INTIMACAO (teste): fica a parte intimada para apresentar contestacao no prazo legal.", "h9001"),
				new DadosBrutos(Fonte.EMAIL, "m-1", n1, "TJDFT", hoje, "Aviso push (teste) sobre o mesmo processo.", "h-m1"),
				new DadosBrutos(Fonte.DJEN, "9002", "0000999-99.2025.8.07.0001", "TJDFT", hoje, "PUBLICACAO (teste) de processo ainda nao cadastrado: sugerir cadastro.", "h9002")));
		pipeline.processar(d.id(), java.util.List.of(
				new DadosBrutos(Fonte.DJEN, "9003", numeroDoProcesso(2), "TRT-10", hoje, "INTIMACAO (teste) do advogado D, que Assistente E nao ve.", "h9003")));
		pipeline.processar(a.id(), java.util.List.of(
				new DadosBrutos(Fonte.EMAIL, "m-2", "0000888-88.2025.5.01.0001", "TRT-1", hoje, "Push do TRT-1 (teste): ignorado pelo filtro.", "h-m2"),
				new DadosBrutos(Fonte.EMAIL, "m-3", numeroDoProcesso(3), "TRT-10", hoje, "Push do TRT-10 (teste): mantido.", "h-m3")));
		// Atendimento e financeiro (so socias veem o financeiro)
		atendimentos.registrarAvulso(assistente, maria.id(), pC.id(), "WHATSAPP", LocalDateTime.of(hoje.minusDays(1), LocalTime.of(11, 30)), "Cliente perguntou sobre a data da audiencia.", e.id());
		atendimentos.registrarAndamento(socia, pC.id(), hoje.minusDays(2), "Peticao protocolada e juntada aos autos.");
		Long contrato = financeiro.criarContrato(socia, maria.id(), pC.id(), "Honorarios contratuais - caso Maria", new java.math.BigDecimal("6000.00"), 6, hoje.minusMonths(2));
		financeiro.pagarParcela(socia, financeiro.parcelas(socia, contrato).get(0).get("id") instanceof Number n ? n.longValue() : 0L, hoje.minusMonths(2), new java.math.BigDecimal("1000.00"));
		financeiro.registrarDespesa(socia, maria.id(), pC.id(), "CUSTAS", "Custas iniciais", new java.math.BigDecimal("350.00"), hoje.minusMonths(2), true);
		financeiro.registrarHonorarioVariavel(socia, maria.id(), pC.id(), "EXITO", "Exito de 10% sobre o valor recebido", new java.math.BigDecimal("1500.00"));
		// Biblioteca de modelos (arquivos minimos ficticios)
		byte[] pdf = "%PDF-1.4\n% modelo ficticio de teste\n".getBytes();
		modelos.criar(socia, "CON", "Civel", "Contestacao padrao - cobranca", "contestacao-cobranca.pdf", pdf);
		modelos.criar(socia, "INI", "Trabalhista", "Peticao inicial - horas extras", "inicial-horas-extras.pdf", pdf);
		modelos.criar(socia, "REC", "Civel", "Apelacao civel", "apelacao-civel.pdf", pdf);
		log.info("Dados ficticios de desenvolvimento criados. Senha de todos os usuarios: {}", SENHA);
	}

	private static String numeroDoProcesso(int n) {
		String seq = String.format("%07d", n);
		String resto = "2025" + "8" + "07" + "0001";
		return seq + NumeroCnj.calcularDv(seq, resto) + resto;
	}

	private ProcessoDto processo(UsuarioDto ator, int n, Long clienteId, Long advogadoId, String tribunal) {
		String seq = String.format("%07d", n);
		String resto = "2025" + "8" + "07" + "0001";
		String numero = seq + NumeroCnj.calcularDv(seq, resto) + resto;
		return cadastros.criarProcesso(ator.id(), numero, tribunal, "1a Vara Ficticia", "Procedimento Comum", "Em andamento",
				new java.math.BigDecimal("15000.00"), clienteId, advogadoId);
	}

	private static UsuarioLogado logado(UsuarioDto u, Perfil perfil) {
		return new UsuarioLogado(u.id(), u.nome(), u.email(), "x", perfil, true);
	}
}
