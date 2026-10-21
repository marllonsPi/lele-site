package br.com.crispimcampos.gestao.importacao;

import java.time.Instant;
import java.util.List;

/** Origem dos arquivos das pastas de clientes (o Drive da escritorio). Somente leitura. */
interface DriveFonte {

	/** {@code caminho}: pastas desde a raiz de clientes (o primeiro elemento e a pasta do cliente; vazio = arquivo solto na raiz). */
	record Arquivo(String id, String nome, String mime, Instant modificadoEm, long tamanho, List<String> caminho) {
	}

	List<Arquivo> listar();

	byte[] baixar(Arquivo arquivo);
}
