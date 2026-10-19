package br.com.crispimcampos.gestao.cadastros;

/** Dados do processo que outros modulos podem usar sem passar pela leitura de tela (titular = advogado responsavel). */
public record ProcessoResumo(Long id, Long titularId, Long clienteId, String tribunal, boolean ativo) {
}
