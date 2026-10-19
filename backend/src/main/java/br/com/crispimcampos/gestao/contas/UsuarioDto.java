package br.com.crispimcampos.gestao.contas;

import java.util.List;

public record UsuarioDto(Long id, String nome, String email, Perfil perfil, boolean ativo, Long supervisorId,
		List<OabDto> oabs, boolean mestre, String telefone, String whatsapp, String cargo, String emailAlertas,
		boolean alertasEmail, String assinatura) {

	public UsuarioDto(Long id, String nome, String email, Perfil perfil, boolean ativo, Long supervisorId,
			List<OabDto> oabs) {
		this(id, nome, email, perfil, ativo, supervisorId, oabs, false, null, null, null, null, true, null);
	}

	/** Para onde vao os alertas: o e-mail dedicado, se houver, senao o de login; nulo quando a pessoa desligou. */
	public String destinoAlertas() {
		if (!alertasEmail) {
			return null;
		}
		return emailAlertas != null ? emailAlertas : email;
	}
}
