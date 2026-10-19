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
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CompromissoEAgendaTest {

	static final long LETICIA = 1, ANDRESSA = 3, WENDELL = 4, LUANA = 5, ESTAGIARIO = 6;
	static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
	// 2026-10-20 12:00 em Sao Paulo
	Clock relogio = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), SP);
	LocalDate hoje = LocalDate.parse("2026-10-20");

	AgendamentoRepository repo = mock(AgendamentoRepository.class);
	CadastroService cadastros = mock(CadastroService.class);
	Visibilidade visibilidade = mock(Visibilidade.class);
	UsuarioService usuarios = mock(UsuarioService.class);
	RegrasAgendamento regras = new RegrasAgendamento(repo, visibilidade, usuarios);
	CompromissoService compromissos = new CompromissoService(repo, cadastros, visibilidade, regras,
			mock(AuditoriaService.class), relogio);
	AgendaService agenda = new AgendaService(repo, visibilidade, relogio);

	UsuarioLogado leticia = ator(LETICIA, Perfil.SOCIA);
	UsuarioLogado andressa = ator(ANDRESSA, Perfil.ADVOGADO);
	UsuarioLogado luana = ator(LUANA, Perfil.ASSISTENTE);
	UsuarioLogado estagiario = ator(ESTAGIARIO, Perfil.ESTAGIARIO);
	LocalDateTime amanha10h = LocalDateTime.parse("2026-10-21T10:00:00");

	static UsuarioLogado ator(long id, Perfil p) {
		return new UsuarioLogado(id, "T" + id, "u" + id + "@exemplo.test", "x", p, true);
	}

	void usuario(long id, Perfil p) {
		when(usuarios.buscar(id)).thenReturn(new UsuarioDto(id, "U" + id, "u" + id + "@exemplo.test", p, true, null, List.of()));
	}

	@BeforeEach
	void prepara() {
		usuario(LETICIA, Perfil.SOCIA);
		usuario(ANDRESSA, Perfil.ADVOGADO);
		usuario(LUANA, Perfil.ASSISTENTE);
		usuario(ESTAGIARIO, Perfil.ESTAGIARIO);
		when(cadastros.resumo(10L)).thenReturn(new ProcessoResumo(10L, ANDRESSA, 20L, "TJDFT", true));
		when(cadastros.clienteExisteAtivo(20L)).thenReturn(true);
		when(cadastros.clienteExisteAtivo(21L)).thenReturn(true);
		when(visibilidade.podeVer(any(), any(), any())).thenReturn(false);
		when(visibilidade.podeVer(leticia, Escopo.AGENDAMENTO, ANDRESSA)).thenReturn(true);
		when(visibilidade.podeVer(leticia, Escopo.AGENDAMENTO, LETICIA)).thenReturn(true);
		when(visibilidade.podeVer(andressa, Escopo.AGENDAMENTO, ANDRESSA)).thenReturn(true);
		when(visibilidade.podeVer(luana, Escopo.AGENDAMENTO, ANDRESSA)).thenReturn(true);
		when(repo.findCompromissosIniciandoEntre(any(), any(), any())).thenReturn(List.of());
		when(repo.save(any())).thenAnswer(i -> {
			Object o = i.getArgument(0);
			ReflectionTestUtils.setField(o, "id", 77L);
			return o;
		});
	}

	// ---- compromissos

	@Test
	void audienciaGuardaHoraModalidadeESemRegraDeTresDiasUteis() {
		var r = compromissos.marcar(leticia, TipoAgendamento.AUD, 10L, null, "instrucao", ANDRESSA, false, amanha10h, null,
				Modalidade.ONLINE, "https://exemplo.test/sala");
		assertThat(r.item().tipo()).isEqualTo(TipoAgendamento.AUD);
		assertThat(r.item().corTipo()).isEqualTo("roxo");
		assertThat(r.item().hora().toString()).isEqualTo("10:00");
		assertThat(r.item().data()).isEqualTo(LocalDate.parse("2026-10-21"));
		assertThat(r.item().duracaoMin()).isEqualTo(60);
		assertThat(r.item().prazoInterno()).isNull();
		assertThat(r.item().status()).isEqualTo(StatusAgendamento.PENDENTE);
		assertThat(r.item().statusVisual()).isEqualTo(StatusVisual.VERDE);
		assertThat(r.avisos()).isEmpty();
	}

	@Test
	void audienciaExigeProcessoLocalOuLinkEModalidadeValida() {
		assertThatThrownBy(() -> compromissos.marcar(leticia, TipoAgendamento.AUD, null, null, null, ANDRESSA, false, amanha10h, null, Modalidade.ONLINE, "x"))
				.isInstanceOf(RegraDeAgendaException.class).hasMessageContaining("processo");
		assertThatThrownBy(() -> compromissos.marcar(leticia, TipoAgendamento.AUD, 10L, null, null, ANDRESSA, false, amanha10h, null, Modalidade.ONLINE, " "))
				.isInstanceOf(RegraDeAgendaException.class).hasMessageContaining("link");
		assertThatThrownBy(() -> compromissos.marcar(leticia, TipoAgendamento.AUD, 10L, null, null, ANDRESSA, false, amanha10h, null, Modalidade.TELEFONE, null))
				.isInstanceOf(RegraDeAgendaException.class);
		verify(repo, never()).save(any());
	}

	@Test
	void atendimentoExigeDuracaoEClienteMasPodeSerSoComCliente() {
		assertThatThrownBy(() -> compromissos.marcar(leticia, TipoAgendamento.ATD, null, 21L, null, LETICIA, false, amanha10h, null, Modalidade.TELEFONE, null))
				.isInstanceOf(RegraDeAgendaException.class).hasMessageContaining("duracao");
		assertThatThrownBy(() -> compromissos.marcar(leticia, TipoAgendamento.ATD, null, null, null, LETICIA, false, amanha10h, 45, Modalidade.TELEFONE, null))
				.isInstanceOf(RegraDeAgendaException.class).hasMessageContaining("cliente");
		var r = compromissos.marcar(leticia, TipoAgendamento.ATD, null, 21L, "primeira consulta", LETICIA, false, amanha10h, 45,
				Modalidade.TELEFONE, null);
		assertThat(r.item().processoId()).isNull();
		assertThat(r.item().clienteId()).isEqualTo(21L);
		assertThat(r.item().corTipo()).isEqualTo("azul");
	}

	@Test
	void atendimentoComProcessoUsaClienteDoProcessoERecusaOutro() {
		var ok = compromissos.marcar(leticia, TipoAgendamento.ATD, 10L, null, null, ANDRESSA, false, amanha10h, 30,
				Modalidade.PRESENCIAL, "Sala 1");
		assertThat(ok.item().clienteId()).isEqualTo(20L);
		assertThatThrownBy(() -> compromissos.marcar(leticia, TipoAgendamento.ATD, 10L, 21L, null, ANDRESSA, false, amanha10h, 30, Modalidade.PRESENCIAL, "Sala 1"))
				.isInstanceOf(RegraDeAgendaException.class).hasMessageContaining("cliente");
	}

	@Test
	void atendimentoSoPorAdvogadoOuSocia() {
		assertThatThrownBy(() -> compromissos.marcar(leticia, TipoAgendamento.ATD, null, 21L, null, LUANA, false, amanha10h, 30, Modalidade.TELEFONE, null))
				.isInstanceOf(RegraDeAgendaException.class);
	}

	@Test
	void luanaMarcaAudienciaParaSiMasNuncaParaAdvogado() {
		assertThatThrownBy(() -> compromissos.marcar(luana, TipoAgendamento.AUD, 10L, null, null, ANDRESSA, false, amanha10h, null, Modalidade.ONLINE, "x"))
				.isInstanceOf(RegraDeAgendaException.class).hasMessageContaining("atribuir");
		assertThat(compromissos.marcar(luana, TipoAgendamento.AUD, 10L, null, null, LUANA, false, amanha10h, null, Modalidade.ONLINE, "x")
				.item().criadoPor()).isEqualTo(LUANA);
	}

	@Test
	void estagiarioNaoMarcaEProcessoInvisivelParecInexistente() {
		assertThatThrownBy(() -> compromissos.marcar(estagiario, TipoAgendamento.AUD, 10L, null, null, ESTAGIARIO, false, amanha10h, null, Modalidade.ONLINE, "x"))
				.isInstanceOf(RegraDeAgendaException.class);
		UsuarioLogado wendell = ator(WENDELL, Perfil.ADVOGADO);
		usuario(WENDELL, Perfil.ADVOGADO);
		assertThatThrownBy(() -> compromissos.marcar(wendell, TipoAgendamento.AUD, 10L, null, null, WENDELL, false, amanha10h, null, Modalidade.ONLINE, "x"))
				.isInstanceOf(AgendaNaoEncontradaException.class);
	}

	@Test
	void conflitoAvisaMasNaoBloqueia() {
		Agendamento existente = Agendamento.marcarCompromisso(10L, ANDRESSA, TipoAgendamento.AUD, null, ANDRESSA, LETICIA, false,
				amanha10h.atZone(SP).toInstant().plusSeconds(1800), 60, Modalidade.ONLINE, "x", null, null);
		ReflectionTestUtils.setField(existente, "id", 5L);
		when(repo.findCompromissosIniciandoEntre(any(), any(), any())).thenReturn(List.of(existente));
		var r = compromissos.marcar(leticia, TipoAgendamento.ATD, null, 21L, null, ANDRESSA, false, amanha10h, 60, Modalidade.PRESENCIAL, "Sala 2");
		assertThat(r.avisos()).extracting(Conflitos.Aviso::tipo).containsExactly(Conflitos.Tipo.MESMO_ADVOGADO);
		assertThat(r.item().id()).isEqualTo(77L);
		verify(repo).save(any());
	}

	// ---- agenda

	Agendamento prazo(long id, long titular, long resp, LocalDate interno, boolean urgente) {
		Agendamento a = Agendamento.lancarPrazo(10L, titular, TipoAgendamento.CON, null, resp, LETICIA, urgente,
				interno.minusDays(10), interno, interno.plusDays(3));
		ReflectionTestUtils.setField(a, "id", id);
		return a;
	}

	Agendamento audiencia(long id, long resp, String dataHora) {
		Agendamento a = Agendamento.marcarCompromisso(10L, ANDRESSA, TipoAgendamento.AUD, null, resp, LETICIA, false,
				LocalDateTime.parse(dataHora).atZone(SP).toInstant(), 60, Modalidade.ONLINE, "x", null, null);
		ReflectionTestUtils.setField(a, "id", id);
		return a;
	}

	@Test
	void agendaOrdenaPorDiaComHorarioPrimeiroECalculaCoresEAlerta() {
		when(visibilidade.titularesVisiveis(andressa, Escopo.AGENDAMENTO)).thenReturn(Set.of(ANDRESSA));
		when(repo.findAgenda(any(), any(), any())).thenReturn(List.of(
				prazo(1, ANDRESSA, ANDRESSA, hoje.minusDays(3), false),
				prazo(2, ANDRESSA, ANDRESSA, hoje, true),
				audiencia(3, ANDRESSA, "2026-10-20T16:00:00"),
				audiencia(4, ANDRESSA, "2026-10-20T09:00:00"),
				prazo(5, ANDRESSA, ANDRESSA, hoje.plusDays(1), false),
				audiencia(6, ANDRESSA, "2026-10-30T09:00:00"),
				audiencia(7, ANDRESSA, "2026-11-02T09:00:00"),
				prazo(8, ANDRESSA, ANDRESSA, hoje.minusDays(31), false)));
		var itens = agenda.agenda(andressa, null);
		assertThat(itens).extracting(AgendaItemDto::id).containsExactly(1L, 4L, 3L, 2L, 5L, 6L, 7L);
		assertThat(itens.get(0).statusVisual()).isEqualTo(StatusVisual.VERMELHO);
		assertThat(itens.get(1).statusVisual()).isEqualTo(StatusVisual.AMARELO);
		assertThat(itens.get(4).statusVisual()).isEqualTo(StatusVisual.VERDE);
		assertThat(itens.get(3).urgente()).isTrue();
		assertThat(itens.get(5).alertaAudiencia()).as("audiencia em 10 dias").isTrue();
		assertThat(itens.get(6).alertaAudiencia()).as("audiencia em 13 dias").isFalse();
		assertThat(itens.get(0).aguardandoConferenciaPrazo()).isTrue();
	}

	@Test
	void agendaDeOutraPessoaSoParaSocia() {
		assertThatThrownBy(() -> agenda.agenda(andressa, LETICIA)).isInstanceOf(RegraDeAgendaException.class);
		assertThatThrownBy(() -> agenda.agenda(luana, ANDRESSA)).isInstanceOf(RegraDeAgendaException.class);
		when(visibilidade.titularesVisiveis(leticia, Escopo.AGENDAMENTO)).thenReturn(Set.of(ANDRESSA));
		agenda.agenda(leticia, ANDRESSA);
		verify(repo).findAgenda(org.mockito.ArgumentMatchers.eq(ANDRESSA), any(), any());
	}

	@Test
	void semTitularesVisiveisAgendaVaziaEEstagiarioVeSoAtribuido() {
		assertThat(agenda.agenda(luana, null)).isEmpty();
		verify(repo, never()).findAgenda(any(), any(), any());
		agenda.agenda(estagiario, null);
		verify(repo).findAgendaAtribuida(org.mockito.ArgumentMatchers.eq(ESTAGIARIO), any());
	}
}
