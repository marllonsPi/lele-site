package br.com.crispimcampos.gestao.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.crispimcampos.gestao.agenda.AgendaItemDto;
import br.com.crispimcampos.gestao.agenda.AgendaService;
import br.com.crispimcampos.gestao.agenda.ConclusaoService;
import br.com.crispimcampos.gestao.agenda.StatusAgendamento;
import br.com.crispimcampos.gestao.agenda.StatusVisual;
import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PainelServiceTest {

	static final LocalDate HOJE = LocalDate.parse("2026-10-20");

	static AgendaItemDto item(long id, TipoAgendamento tipo, long resp, LocalDate data, boolean urgente, boolean aguardandoPrazo) {
		return new AgendaItemDto(id, 1L, null, tipo, "x", null, resp, 1L, urgente,
				aguardandoPrazo ? StatusAgendamento.AGUARDANDO_CONFERENCIA_PRAZO : StatusAgendamento.PENDENTE, data,
				tipo == TipoAgendamento.AUD || tipo == TipoAgendamento.ATD ? LocalTime.of(10, 0) : null, null, null, null,
				null, null, null, StatusVisual.NENHUM, false, aguardandoPrazo);
	}

	@Test
	void calculaCargaAtrasosSemanaECompromissos() {
		var itens = List.of(
				item(1, TipoAgendamento.CON, 3, HOJE.minusDays(2), true, false),
				item(2, TipoAgendamento.MAN, 3, HOJE.plusDays(3), false, false),
				item(3, TipoAgendamento.REC, 4, HOJE.plusDays(9), false, false),
				item(4, TipoAgendamento.AUD, 4, HOJE.plusDays(5), false, false),
				item(5, TipoAgendamento.ATD, 3, HOJE.plusDays(20), false, false),
				item(6, TipoAgendamento.INI, 4, HOJE, false, true));
		var p = PainelService.montar(itens, List.of(), HOJE, 2);
		assertThat(p.porPessoa()).extracting("usuarioId", "abertos", "vencidos", "urgentes")
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(3L, 3L, 1L, 1L), org.assertj.core.groups.Tuple.tuple(4L, 3L, 0L, 0L));
		assertThat(p.atrasos()).extracting(AgendaItemDto::id).containsExactly(1L);
		assertThat(p.prazosDaSemana()).extracting(AgendaItemDto::id).containsExactlyInAnyOrder(2L, 6L);
		assertThat(p.compromissosProximos()).extracting(AgendaItemDto::id).containsExactly(4L);
		assertThat(p.aguardandoConferenciaPrazo()).extracting(AgendaItemDto::id).containsExactly(6L);
		assertThat(p.aClassificar()).isEqualTo(2);
	}

	@Test
	void painelEExclusivoDasSocias() {
		var servico = new PainelService(mock(AgendaService.class), mock(ConclusaoService.class),
				mock(br.com.crispimcampos.gestao.captura.PublicacaoService.class),
				mock(br.com.crispimcampos.gestao.cadastros.CadastroService.class), Clock.systemDefaultZone());
		var luana = new UsuarioLogado(5L, "L", "l@exemplo.test", "x", Perfil.ASSISTENTE, true);
		assertThatThrownBy(() -> servico.painel(luana)).isInstanceOf(RuntimeException.class);
	}
}
