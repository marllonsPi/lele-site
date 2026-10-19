package br.com.crispimcampos.gestao.cadastros;

public record ClienteDto(Long id, String nome, String documento, String email, String telefone, String endereco,
		String observacoes, boolean ativo) {

	static ClienteDto de(Cliente c) {
		return new ClienteDto(c.getId(), c.getNome(), c.getDocumento(), c.getEmail(), c.getTelefone(), c.getEndereco(),
				c.getObservacoes(), c.isAtivo());
	}
}
