package br.com.crispimcampos.gestao.contas;

import java.util.List;

public record UsuarioDto(Long id, String nome, String email, Perfil perfil, boolean ativo, Long supervisorId,
		List<OabDto> oabs) {
}
