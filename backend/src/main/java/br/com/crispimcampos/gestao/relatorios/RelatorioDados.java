package br.com.crispimcampos.gestao.relatorios;

import java.util.List;

/** Relatorio interno em forma de tabela (serve a tela, ao PDF e ao e-mail). */
public record RelatorioDados(String titulo, String subtitulo, List<String> colunas, List<List<String>> linhas, String geradoEm) {
}
