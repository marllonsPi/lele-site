package br.com.crispimcampos.gestao.captura;

import java.time.LocalDate;
import java.util.List;

public record PublicacaoDto(Long id, String numeroProcesso, String numeroFormatado, Long titularId, LocalDate dataPublicacao,
		String tribunal, String resumo, List<Fonte> fontes, boolean somenteEmUmaFonte, EstadoPublicacao estado,
		Long processoId, boolean sugerirCadastro, Long agendamentoId, String motivoDescarte, String conteudo) {

	static PublicacaoDto de(Publicacao p, List<Fonte> fontes, String conteudo) {
		String n = p.getNumeroProcesso();
		boolean cnj = n.length() == 20 && n.chars().allMatch(Character::isDigit);
		return new PublicacaoDto(p.getId(), n, cnj ? br.com.crispimcampos.gestao.cadastros.NumeroCnj.formatar(n) : null,
				p.getTitularId(), p.getDataPublicacao(), p.getTribunal(), p.getResumo(), fontes, fontes.size() == 1,
				p.getEstado(), p.getProcessoId(), cnj && p.getProcessoId() == null, p.getAgendamentoId(), p.getMotivoDescarte(),
				conteudo);
	}
}
