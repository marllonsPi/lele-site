package br.com.crispimcampos.gestao.painel;

import br.com.crispimcampos.gestao.agenda.AgendaItemDto;
import java.util.List;

/** Painel das socias (item 11): tudo derivado de itens que a socia ja pode ver. */
public record PainelDto(List<CargaPessoa> porPessoa, List<AgendaItemDto> atrasos, List<AgendaItemDto> prazosDaSemana,
		List<AgendaItemDto> compromissosProximos, List<AgendaItemDto> aguardandoConferenciaSocia,
		List<AgendaItemDto> aguardandoConferenciaPrazo, long aClassificar) {

	/** Carga de trabalho por responsavel. */
	public record CargaPessoa(Long usuarioId, long abertos, long vencidos, long urgentes) {
	}
}
