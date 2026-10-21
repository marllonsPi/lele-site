package br.com.crispimcampos.gestao.cadastros;

import java.util.Comparator;
import java.util.List;

/**
 * {@code cadastroIncompleto}: cliente sem area de atuacao (alerta na tela ate ser preenchida). {@code aConferir}: veio de
 * importacao sem CPF/CNPJ ou com dados a confirmar.
 */
public record ClienteDto(Long id, String nome, String documento, String email, String telefone, String endereco,
		String observacoes, boolean ativo, List<AreaAtuacao> areas, boolean cadastroIncompleto, boolean aConferir, String rg,
		String estadoCivil) {

	static ClienteDto de(Cliente c) {
		return new ClienteDto(c.getId(), c.getNome(), c.getDocumento(), c.getEmail(), c.getTelefone(), c.getEndereco(),
				c.getObservacoes(), c.isAtivo(), c.getAreas().stream().sorted(Comparator.naturalOrder()).toList(),
				c.incompleto(), c.isAConferir(), c.getRg(), c.getEstadoCivil());
	}
}
