package br.com.crispimcampos.gestao.captura;

/** OAB ativa de um advogado: o que a captura consulta. */
public record OabMonitorada(Long titularId, String numero, String uf) {
}
