package br.com.crispimcampos.gestao.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.crispimcampos.gestao.acesso.Escopo;
import br.com.crispimcampos.gestao.acesso.Visibilidade;
import br.com.crispimcampos.gestao.auditoria.AuditoriaService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioDto;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import br.com.crispimcampos.gestao.contas.UsuarioService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ConclusaoServiceTest {

	static final long LETICIA = 1, LORRAYNNY = 2, ANDRESSA = 3, WENDELL = 4, LUANA = 5, ESTAGIARIO = 6;

	AgendamentoRepository repo = mock(AgendamentoRepository.class);
	ConferenciaRepository conferencias = mock(ConferenciaRepository.class);
	ObservacaoRepository observacoes = mock(ObservacaoRepository.class);
	Visibilidade visibilidade = mock(Visibilidade.class);
	UsuarioService usuarios = mock(UsuarioService.class);
	Clock relogio = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
	ConclusaoService servico = new ConclusaoService(repo, conferencias, observacoes,
			new RegrasAgendamento(repo, visibilidade, usuarios), visibilidade, usuarios, mock(AuditoriaService.class), relogio,
			mock(org.springframework.context.ApplicationEventPublisher.class));

	UsuarioLogado leticia = new UsuarioLogado(LETICIA, "L", "l@exemplo.test", "x", Perfil.SOCIA, true);
	UsuarioLogado lorraynny = new UsuarioLogado(LORRAYNNY, "Lo", "lo@exemplo.test", "x", Perfil.SOCIA, true);
	UsuarioLogado andressa = new UsuarioLogado(ANDRESSA, "A", "a@exemplo.test", "x", Perfil.ADVOGADO, true);
	UsuarioLogado wendell = new UsuarioLogado(WENDELL, "W", "w@exemplo.test", "x", Perfil.ADVOGADO, true);
	UsuarioLogado luana = new UsuarioLogado(LUANA, "Lu", "lu@exemplo.test", "x", Perfil.ASSISTENTE, true);
	UsuarioLogado estagiario = new UsuarioLogado(ESTAGIARIO, "E", "e@exemplo.test", "x", Perfil.ESTAGIARIO, true);

	static final LocalDate D = LocalDate.parse("2026-10-20");

	void usuario(long id, Perfil p, Long supervisor) {
		when(usuarios.buscar(id)).thenReturn(new UsuarioDto(id, "U" + id, "u" + id + "@exemplo.test", p, true, supervisor, List.of()));
	}

	/** Prazo ja conferido (PENDENTE) em nome de Andressa, responsavel dado. */
	Agendamento pendente(long responsavel) {
		Agendamento a = Agendamento.lancarPrazo(10L, ANDRESSA, TipoAgendamento.CON, null, responsavel, LETICIA, false,
				D.minusDays(5), D, D.plusDays(3));
		a.conferirPrazo(LORRAYNNY, relogio.instant());
		ReflectionTestUtils.setField(a, "id", 50L);
		when(repo.findById(50L)).thenReturn(Optional.of(a));
		return a;
	}

	@BeforeEach
	void prepara() {
		usuario(LETICIA, Perfil.SOCIA, null);
		usuario(LORRAYNNY, Perfil.SOCIA, null);
		usuario(ANDRESSA, Perfil.ADVOGADO, null);
		usuario(WENDELL, Perfil.ADVOGADO, null);
		usuario(LUANA, Perfil.ASSISTENTE, null);
		usuario(ESTAGIARIO, Perfil.ESTAGIARIO, ANDRESSA);
		for (UsuarioLogado u : List.of(leticia, lorraynny, andressa, luana)) {
			when(visibilidade.podeVer(u, Escopo.AGENDAMENTO, ANDRESSA)).thenReturn(true);
		}
		when(conferencias.save(any())).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void responsavelFinalizaEVaiParaConferenciaDasSocias() {
		Agendamento a = pendente(ANDRESSA);
		var dto = servico.finalizar(andressa, 50L);
		assertThat(dto.status()).isEqualTo(StatusAgendamento.EM_CONFERENCIA_SOCIA);
		assertThat(a.getFinalizadoPor()).isEqualTo(ANDRESSA);
		assertThat(a.getFinalizadoEm()).isEqualTo(relogio.instant());
	}

	@Test
	void estagiarioFinalizaEVaiPrimeiroAoSupervisor() {
		Agendamento a = pendente(ESTAGIARIO);
		when(visibilidade.podeVer(estagiario, Escopo.AGENDAMENTO, ANDRESSA)).thenReturn(false);
		assertThat(servico.finalizar(estagiario, 50L).status()).isEqualTo(StatusAgendamento.EM_CONFERENCIA_SUPERVISOR);
		assertThat(a.getStatus()).isEqualTo(StatusAgendamento.EM_CONFERENCIA_SUPERVISOR);
	}

	@Test
	void soResponsavelOuSociaFinalizaEPrazoNaoConferidoNao() {
		pendente(ANDRESSA);
		assertThatThrownBy(() -> servico.finalizar(luana, 50L)).isInstanceOf(RegraDeAgendaException.class);
		Agendamento aguardando = Agendamento.lancarPrazo(10L, ANDRESSA, TipoAgendamento.CON, null, ANDRESSA, LUANA, false, D.minusDays(5), D, D.plusDays(3));
		ReflectionTestUtils.setField(aguardando, "id", 51L);
		when(repo.findById(51L)).thenReturn(Optional.of(aguardando));
		assertThatThrownBy(() -> servico.finalizar(andressa, 51L)).isInstanceOf(RegraDeAgendaException.class).hasMessageContaining("conferencia");
	}

	@Test
	void naoFinalizaDuasVezes() {
		pendente(ANDRESSA);
		servico.finalizar(andressa, 50L);
		assertThatThrownBy(() -> servico.finalizar(andressa, 50L)).isInstanceOf(RegraDeAgendaException.class);
	}

	@Test
	void supervisorAprovaOuDevolveSoOsSeusEstagiarios() {
		Agendamento a = pendente(ESTAGIARIO);
		a.finalizar(ESTAGIARIO, true, relogio.instant());
		assertThatThrownBy(() -> servico.aprovarSupervisor(wendell, 50L)).isInstanceOf(AgendaNaoEncontradaException.class);
		assertThat(servico.aprovarSupervisor(andressa, 50L).status()).isEqualTo(StatusAgendamento.EM_CONFERENCIA_SOCIA);
		verify(conferencias).save(any());
	}

	@Test
	void devolucaoExigeMotivoERegistraNoHistorico() {
		Agendamento a = pendente(ANDRESSA);
		a.finalizar(ANDRESSA, false, relogio.instant());
		assertThatThrownBy(() -> servico.devolverSocia(leticia, 50L, "  ")).isInstanceOf(RegraDeAgendaException.class);
		var dto = servico.devolverSocia(leticia, 50L, "Falta anexar o protocolo");
		assertThat(dto.status()).isEqualTo(StatusAgendamento.DEVOLVIDO);
		var c = ArgumentCaptor.forClass(ObservacaoAgendamento.class);
		verify(observacoes).save(c.capture());
		assertThat(c.getValue().getTexto()).contains("Falta anexar o protocolo");
		// devolvido volta ao responsavel e pode ser finalizado de novo
		assertThat(servico.finalizar(andressa, 50L).status()).isEqualTo(StatusAgendamento.EM_CONFERENCIA_SOCIA);
	}

	@Test
	void somenteSociasConferem() {
		Agendamento a = pendente(ANDRESSA);
		a.finalizar(ANDRESSA, false, relogio.instant());
		for (UsuarioLogado u : List.of(andressa, luana, estagiario)) {
			assertThatThrownBy(() -> servico.conferirSocia(u, 50L)).isInstanceOf(RegraDeAgendaException.class);
			assertThatThrownBy(() -> servico.devolverSocia(u, 50L, "x")).isInstanceOf(RegraDeAgendaException.class);
		}
		assertThat(servico.conferirSocia(leticia, 50L).status()).isEqualTo(StatusAgendamento.CONFERIDO);
	}

	@Test
	void socianaoConfereOQueNaoAguardaEFilaVemDosTitularesVisiveis() {
		Agendamento a = pendente(ANDRESSA);
		assertThatThrownBy(() -> servico.conferirSocia(leticia, 50L)).isInstanceOf(IllegalStateException.class);
		a.finalizar(ANDRESSA, false, relogio.instant());
		when(visibilidade.titularesVisiveis(leticia, Escopo.AGENDAMENTO)).thenReturn(Set.of(ANDRESSA));
		when(repo.findByTitularIdInAndStatusIn(Set.of(ANDRESSA), Set.of(StatusAgendamento.EM_CONFERENCIA_SOCIA))).thenReturn(List.of(a));
		assertThat(servico.pendentesDeConferencia(leticia)).hasSize(1);
		assertThat(servico.pendentesDeConferencia(luana)).isEmpty();
	}
}
