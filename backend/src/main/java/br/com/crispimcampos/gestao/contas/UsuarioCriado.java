package br.com.crispimcampos.gestao.contas;

/** Evento publicado quando um usuario e cadastrado; outros modulos reagem sem que `contas` os conheca. */
public record UsuarioCriado(Long id, Perfil perfil) {
}
