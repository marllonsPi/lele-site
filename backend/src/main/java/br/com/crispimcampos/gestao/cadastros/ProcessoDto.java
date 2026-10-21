package br.com.crispimcampos.gestao.cadastros;

import java.math.BigDecimal;
import java.util.List;

public record ProcessoDto(Long id, String numero, String numeroFormatado, String tribunal, String varaComarca,
		String classe, String situacao, BigDecimal valorCausa, Long clienteId, String clienteNome, Long advogadoId,
		boolean ativo, AreaAtuacao area, List<ParteDto> partes) {

	public record ParteDto(Long id, String nome, PapelParte papel) {
	}

	static ProcessoDto de(Processo p, List<ParteProcesso> partes) {
		return new ProcessoDto(p.getId(), p.getNumero(), NumeroCnj.formatar(p.getNumero()), p.getTribunal(),
				p.getVaraComarca(), p.getClasse(), p.getSituacao(), p.getValorCausa(), p.getCliente().getId(),
				p.getCliente().getNome(), p.getAdvogadoId(), p.isAtivo(), p.getArea(),
				partes.stream().map(x -> new ParteDto(x.getId(), x.getNome(), x.getPapel())).toList());
	}
}
