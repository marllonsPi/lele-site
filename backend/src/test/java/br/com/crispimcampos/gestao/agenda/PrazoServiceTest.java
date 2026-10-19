package br.com.crispimcampos.gestao.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.cadastros.ProcessoResumo;
import br.com.crispimcampos.gestao.calendario.CalendarioService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PrazoServiceTest {

	static final long LETICIA = 1, ANDRESSA = 3, WENDELL = 4, LUANA = 5, ESTAGIARIO = 6;
	static final LocalDate INICIO = LocalDate.parse("2026-10-01"), FATAL = LocalDate.parse("2026-10-14"),
			INTERNO = LocalDate.parse("2026-10-08");

	AgendamentoRepository repo = mock(AgendamentoRepository.class);
	CadastroService cadastros = mock(CadastroService.class);
	CalendarioService calendario = mock(CalendarioService.class);
	Visibilidade visibilidade = mock(Visibilidade.class);
	UsuarioService usuarios = mock(UsuarioService.class);
	Clock relogio = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
	PrazoService servico = new PrazoService(repo, cadastros, calendario, visibilidade,
			new RegrasAgendamento(repo, visibilidade, usuarios), mock(AuditoriaService.class), relogio);

	UsuarioLogado leticia = ator(LETICIA, Perfil.SOCIA);
	UsuarioLogado andressa = ator(ANDRESSA, Perfil.ADVOGADO);
	UsuarioLogado luana = ator(LUANA, Perfil.ASSISTENTE);
	UsuarioLogado estagiario = ator(ESTAGIARIO, Perfil.ESTAGIARIO);

	static UsuarioLogado ator(long id, Perfil p) {
		return new UsuarioLogado(id, "Teste " + id, "u" + id + "@exemplo.test", "x", p, true);
	}

	void usuario(long id, Perfil p) {
		when(usuarios.buscar(id)).thenReturn(new UsuarioDto(id, "U" + id, "u" + id + "@exemplo.test", p, true, null, List.of()));
	}

	@BeforeEach
	void prepara() {
		when(cadastros.resumo(10L)).thenReturn(new ProcessoResumo(10L, ANDRESSA, 1L, "TJDFT", true));
		when(calendario.subtrairDiasUteis(any(), any(), org.mockito.ArgumentMatchers.eq(3))).thenReturn(INTERNO);
		when(repo.save(any())).thenAnswer(i -> {
			Object salvo = i.getArgument(0);
			ReflectionTestUtils.setField(salvo, "id", 99L);
			return salvo;
		});
		usuario(LETICIA, Perfil.SOCIA);
		usuario(ANDRESSA, Perfil.ADVOGADO);
		usuario(LUANA, Perfil.ASSISTENTE);
		usuario(ESTAGIARIO, Perfil.ESTAGIARIO);
		usuario(WENDELL, Perfil.ADVOGADO);
	}

	void veAgendamentoDeAndressa(UsuarioLogado... quem) {
		for (UsuarioLogado u : quem) {
			when(visibilidade.podeVer(u, Escopo.AGENDAMENTO, ANDRESSA)).thenReturn(true);
		}
	}

	PrazoDto lancadoPorLuana() {
		veAgendamentoDeAndressa(luana);
		return servico.lancar(luana, 10L, TipoAgendamento.CON, "contestacao", LUANA, false, INICIO, FATAL);
	}

	Agendamento aguardando(long lancadoPor) {
		Agendamento a = Agendamento.lancarPrazo(10L, ANDRESSA, TipoAgendamento.CON, null, LUANA, lancadoPor, false, INICIO, INTERNO, FATAL);
		ReflectionTestUtils.setField(a, "id", 99L);
		when(repo.findById(99L)).thenReturn(Optional.of(a));
		return a;
	}

	@Test
	void lancamentoCalculaInternoETravaEmAguardandoConferencia() {
		PrazoDto dto = lancadoPorLuana();
		assertThat(dto.prazoInterno()).isEqualTo(INTERNO);
		assertThat(dto.prazoFatal()).isEqualTo(FATAL);
		assertThat(dto.status()).isEqualTo(StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO);
		assertThat(dto.lancadoPor()).isEqualTo(LUANA);
		assertThat(dto.conferidoPor()).isNull();
		assertThat(dto.cor()).isEqualTo("laranja");
	}

	@Test
	void quemLancouNaoConfirma() {
		aguardando(LUANA);
		veAgendamentoDeAndressa(luana);
		assertThatThrownBy(() -> servico.conferir(luana, 99L)).isInstanceOf(RegraDeAgendaException.class);
	}

	@Test
	void estagiarioNuncaConfirma() {
		aguardando(LETICIA);
		assertThatThrownBy(() -> servico.conferir(estagiario, 99L)).isInstanceOf(RegraDeAgendaException.class)
				.hasMessageContaining("Estagiario");
	}

	@Test
	void outroUsuarioConfirmaERegistraQuemConferiu() {
		aguardando(LUANA);
		veAgendamentoDeAndressa(leticia);
		PrazoDto dto = servico.conferir(leticia, 99L);
		assertThat(dto.status()).isEqualTo(StatusAgendamento.PENDENTE);
		assertThat(dto.conferidoPor()).isEqualTo(LETICIA);
		assertThat(dto.conferidoEm()).isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
		assertThat(dto.lancadoPor()).isEqualTo(LUANA);
	}

	@Test
	void naoConfirmaDuasVezes() {
		aguardando(LUANA);
		veAgendamentoDeAndressa(leticia);
		servico.conferir(leticia, 99L);
		assertThatThrownBy(() -> servico.conferir(leticia, 99L)).isInstanceOf(RegraDeAgendaException.class);
	}

	@Test
	void semVisibilidadeNaoConfereNemVe() {
		aguardando(LUANA);
		UsuarioLogado wendell = ator(WENDELL, Perfil.ADVOGADO);
		assertThatThrownBy(() -> servico.conferir(wendell, 99L)).isInstanceOf(AgendaNaoEncontradaException.class);
		assertThatThrownBy(() -> servico.buscar(wendell, 99L)).isInstanceOf(AgendaNaoEncontradaException.class);
	}

	@Test
	void prazoConferidoNaoPodeSerCorrigido() {
		Agendamento a = aguardando(LUANA);
		veAgendamentoDeAndressa(luana, leticia);
		a.conferirPrazo(LETICIA, relogio.instant());
		assertThatThrownBy(() -> servico.corrigir(luana, 99L, null, LUANA, false, INICIO, FATAL))
				.isInstanceOf(RegraDeAgendaException.class);
	}

	@Test
	void soQuemLancouOuSociaCorrige() {
		aguardando(LUANA);
		veAgendamentoDeAndressa(andressa, luana, leticia);
		assertThatThrownBy(() -> servico.corrigir(andressa, 99L, null, ANDRESSA, false, INICIO, FATAL))
				.isInstanceOf(RegraDeAgendaException.class);
		assertThat(servico.corrigir(luana, 99L, "novo", LUANA, true, INICIO, FATAL).urgente()).isTrue();
		assertThat(servico.corrigir(leticia, 99L, "outro", LUANA, true, INICIO, FATAL).descricao()).isEqualTo("outro");
	}

	@Test
	void luanaNaoAtribuiAAdvogadoNemASociaMasAtribuiAEstagiario() {
		veAgendamentoDeAndressa(luana);
		for (long alvo : new long[] {ANDRESSA, LETICIA}) {
			assertThatThrownBy(() -> servico.lancar(luana, 10L, TipoAgendamento.MAN, null, alvo, false, INICIO, FATAL))
					.isInstanceOf(RegraDeAgendaException.class);
		}
		assertThat(servico.lancar(luana, 10L, TipoAgendamento.MAN, null, ESTAGIARIO, false, INICIO, FATAL).responsavelId())
				.isEqualTo(ESTAGIARIO);
	}

	@Test
	void sociaAtribuiAQualquerUsuarioAtivoEAdvogadoSoASiMesmo() {
		veAgendamentoDeAndressa(leticia, andressa);
		assertThat(servico.lancar(leticia, 10L, TipoAgendamento.REC, null, ANDRESSA, false, INICIO, FATAL)).isNotNull();
		assertThatThrownBy(() -> servico.lancar(andressa, 10L, TipoAgendamento.REC, null, LUANA, false, INICIO, FATAL))
				.isInstanceOf(RegraDeAgendaException.class);
	}

	@Test
	void naoLancaEmProcessoForaDaVisibilidade() {
		UsuarioLogado wendell = ator(WENDELL, Perfil.ADVOGADO);
		assertThatThrownBy(() -> servico.lancar(wendell, 10L, TipoAgendamento.CON, null, WENDELL, false, INICIO, FATAL))
				.isInstanceOf(AgendaNaoEncontradaException.class);
		verify(repo, never()).save(any());
	}

	@Test
	void audienciaEAtendimentoNaoSaoPrazo() {
		veAgendamentoDeAndressa(leticia);
		for (TipoAgendamento t : new TipoAgendamento[] {TipoAgendamento.AUD, TipoAgendamento.ATD}) {
			assertThatThrownBy(() -> servico.lancar(leticia, 10L, t, null, LETICIA, false, INICIO, FATAL))
					.isInstanceOf(RegraDeAgendaException.class);
		}
	}

	@Test
	void estagiarioNaoLancaEFatalNaoPodeAnteceder() {
		assertThatThrownBy(() -> servico.lancar(estagiario, 10L, TipoAgendamento.MAN, null, ESTAGIARIO, false, INICIO, FATAL))
				.isInstanceOf(RegraDeAgendaException.class);
		veAgendamentoDeAndressa(leticia);
		assertThatThrownBy(() -> servico.lancar(leticia, 10L, TipoAgendamento.MAN, null, LETICIA, false, FATAL, INICIO))
				.isInstanceOf(RegraDeAgendaException.class);
	}

	@Test
	void listagemUsaTitularesVisiveisEEstagiarioSoVeOAtribuido() {
		assertThat(servico.listar(andressa)).isEmpty();
		verify(repo, never()).findByTitularIdInOrderByPrazoInternoAscIdAsc(any());
		when(visibilidade.titularesVisiveis(andressa, Escopo.AGENDAMENTO)).thenReturn(Set.of(ANDRESSA));
		servico.listar(andressa);
		verify(repo).findByTitularIdInOrderByPrazoInternoAscIdAsc(Set.of(ANDRESSA));
		servico.listar(estagiario);
		verify(repo).findByResponsavelIdOrderByPrazoInternoAscIdAsc(ESTAGIARIO);
	}

	@Test
	void dominioRecusaConferirPeloLancadorMesmoSemServico() {
		Agendamento a = aguardando(LUANA);
		assertThatThrownBy(() -> a.conferirPrazo(LUANA, relogio.instant())).isInstanceOf(IllegalStateException.class);
	}
}
