package br.com.crispimcampos.gestao.painel;

import br.com.crispimcampos.gestao.agenda.AgendaItemDto;
import br.com.crispimcampos.gestao.agenda.AgendaService;
import br.com.crispimcampos.gestao.agenda.ConclusaoService;
import br.com.crispimcampos.gestao.agenda.StatusAgendamento;
import br.com.crispimcampos.gestao.agenda.TipoAgendamento;
import br.com.crispimcampos.gestao.cadastros.AreaAtuacao;
import br.com.crispimcampos.gestao.cadastros.CadastroService;
import br.com.crispimcampos.gestao.captura.PublicacaoService;
import br.com.crispimcampos.gestao.contas.Perfil;
import br.com.crispimcampos.gestao.contas.UsuarioLogado;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Visao exclusiva das socias (item 11). */
@Service
public class PainelService {

	static final int DIAS_SEMANA = 7;
	static final int DIAS_COMPROMISSOS = 14;

	private final AgendaService agenda;
	private final ConclusaoService conclusao;
	private final PublicacaoService publicacoes;
	private final CadastroService cadastros;
	private final Clock relogio;

	PainelService(AgendaService agenda, ConclusaoService conclusao, PublicacaoService publicacoes, CadastroService cadastros,
			Clock relogio) {
		this.agenda = agenda;
		this.conclusao = conclusao;
		this.publicacoes = publicacoes;
		this.cadastros = cadastros;
		this.relogio = relogio;
	}

	@Transactional(readOnly = true)
	public PainelDto painel(UsuarioLogado ator) {
		return painel(ator, null);
	}

	/** {@code area} opcional: so itens de processos (ou, sem processo, de clientes) dessa area. */
	@Transactional(readOnly = true)
	public PainelDto painel(UsuarioLogado ator, AreaAtuacao area) {
		if (ator.perfil() != Perfil.SOCIA) {
			throw new AcessoNegadoException("O painel e exclusivo das socias");
		}
		LocalDate hoje = LocalDate.now(relogio);
		List<AgendaItemDto> abertos = filtrarPorArea(agenda.todosAbertos(ator), area);
		return montar(abertos, filtrarPorArea(conclusao.pendentesDeConferencia(ator).stream()
				.filter(i -> i.status() == StatusAgendamento.EM_CONFERENCIA_SOCIA).toList(), area), hoje,
				publicacoes.contarAClassificar(ator));
	}

	private List<AgendaItemDto> filtrarPorArea(List<AgendaItemDto> itens, AreaAtuacao area) {
		if (area == null) {
			return itens;
		}
		Map<Long, AreaAtuacao> dosProcessos = cadastros.areasDosProcessos(
				itens.stream().map(AgendaItemDto::processoId).filter(java.util.Objects::nonNull).distinct().toList());
		return itens.stream().filter(i -> i.processoId() != null ? dosProcessos.get(i.processoId()) == area
				: i.clienteId() != null && cadastros.areasDoCliente(i.clienteId()).contains(area)).toList();
	}

	/** Calculo puro, testavel sem banco. */
	static PainelDto montar(List<AgendaItemDto> abertos, List<AgendaItemDto> emConferenciaSocia, LocalDate hoje, long aClassificar) {
		Map<Long, long[]> carga = new TreeMap<>();
		for (AgendaItemDto i : abertos) {
			long[] c = carga.computeIfAbsent(i.responsavelId(), k -> new long[3]);
			c[0]++;
			if (i.data().isBefore(hoje)) {
				c[1]++;
			}
			if (i.urgente()) {
				c[2]++;
			}
		}
		List<PainelDto.CargaPessoa> porPessoa = carga.entrySet().stream()
				.map(e -> new PainelDto.CargaPessoa(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
				.sorted(Comparator.comparingLong(PainelDto.CargaPessoa::abertos).reversed()).toList();
		List<AgendaItemDto> atrasos = abertos.stream().filter(i -> i.data().isBefore(hoje)).toList();
		List<AgendaItemDto> semana = abertos.stream()
				.filter(i -> i.tipo() != TipoAgendamento.AUD && i.tipo() != TipoAgendamento.ATD)
				.filter(i -> !i.data().isBefore(hoje) && !i.data().isAfter(hoje.plusDays(DIAS_SEMANA))).toList();
		List<AgendaItemDto> compromissos = abertos.stream()
				.filter(i -> i.tipo() == TipoAgendamento.AUD || i.tipo() == TipoAgendamento.ATD)
				.filter(i -> !i.data().isBefore(hoje) && !i.data().isAfter(hoje.plusDays(DIAS_COMPROMISSOS))).toList();
		List<AgendaItemDto> aguardandoPrazo = abertos.stream().filter(AgendaItemDto::aguardandoConferenciaPrazo).toList();
		return new PainelDto(porPessoa, atrasos, semana, compromissos, emConferenciaSocia, aguardandoPrazo, aClassificar);
	}
}
