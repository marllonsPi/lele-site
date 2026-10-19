package br.com.crispimcampos.gestao.agenda;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Disco local protegido: nome gravado e um UUID (nunca o nome enviado), fora de qualquer pasta publica. */
@Component
public class ArmazenamentoAnexos {

	private static final Pattern CAMINHO = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private final Path raiz;

	ArmazenamentoAnexos(@Value("${app.storage.dir:./storage}") String dir) {
		this.raiz = Path.of(dir).toAbsolutePath().normalize();
	}

	public String salvar(byte[] conteudo) {
		String nome = UUID.randomUUID().toString();
		try {
			Files.createDirectories(raiz);
			Files.write(raiz.resolve(nome), conteudo);
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao gravar anexo", e);
		}
		return nome;
	}

	public Resource abrir(String caminho) {
		if (!CAMINHO.matcher(caminho).matches()) {
			throw new IllegalArgumentException("Caminho de anexo invalido");
		}
		Path p = raiz.resolve(caminho).normalize();
		if (!p.startsWith(raiz) || !Files.isReadable(p)) {
			throw new AgendaNaoEncontradaException("Arquivo nao encontrado");
		}
		return new FileSystemResource(p);
	}
}
