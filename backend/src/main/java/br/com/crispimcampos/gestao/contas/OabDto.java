package br.com.crispimcampos.gestao.contas;

public record OabDto(Long id, String numero, String uf, boolean principal, boolean ativa) {

	static OabDto de(Oab o) {
		return new OabDto(o.getId(), o.getNumero(), o.getUf(), o.isPrincipal(), o.isAtiva());
	}
}
