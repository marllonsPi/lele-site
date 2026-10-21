package br.com.crispimcampos.gestao.agenda;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import br.com.crispimcampos.gestao.drive.DriveEscritorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Armazenamento dos anexos. Com o Drive do escritorio ativo, o arquivo vai para o Drive (caminho {@code drive:<id>}); sem ele, ou se o
 * Drive falhar, fica no disco local protegido (nome = UUID, nunca o nome enviado, fora de pasta publica). Os dois convivem.
 */
@Component
public class ArmazenamentoAnexos {

	private static final Pattern CAMINHO = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	private static final Logger log = LoggerFactory.getLogger(ArmazenamentoAnexos.class);
	public static final String PREFIXO_DRIVE = "drive:";
	private static final Pattern CAMINHO_DRIVE = Pattern.compile("drive:[A-Za-z0-9_-]{10,120}");

	private final Path raiz;
	private DriveEscritorio drive;

	ArmazenamentoAnexos(@Value("${app.storage.dir:./storage}") String dir) {
		this.raiz = Path.of(dir).toAbsolutePath().normalize();
	}

	@Autowired(required = false)
	void ligarDrive(DriveEscritorio drive) {
		this.drive = drive;
	}

	/** Preferencia pelo Drive do escritorio (em {@code pastas} sob a raiz); cai no disco local se o Drive estiver inativo ou falhar. */
	public String salvarPreferindoDrive(List<String> pastas, String nome, String mime, byte[] conteudo) {
		if (drive != null && drive.ativo()) {
			try {
				return PREFIXO_DRIVE + drive.enviar(pastas, nome, mime, conteudo);
			} catch (RuntimeException e) {
				log.warn("Falha ao gravar no Drive ({}); o arquivo ficou no armazenamento local", e.getClass().getSimpleName());
			}
		}
		return salvar(conteudo);
	}

	public static boolean noDrive(String caminho) {
		return caminho != null && caminho.startsWith(PREFIXO_DRIVE);
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
		if (CAMINHO_DRIVE.matcher(caminho).matches()) {
			if (drive == null) {
				throw new AgendaNaoEncontradaException("Drive do escritorio indisponivel");
			}
			try {
				return new ByteArrayResource(drive.baixar(caminho.substring(PREFIXO_DRIVE.length())));
			} catch (RuntimeException e) {
				log.warn("Falha ao ler do Drive: {}", e.getClass().getSimpleName());
				throw new AgendaNaoEncontradaException("Arquivo indisponivel no Drive agora; tente de novo em instantes");
			}
		}
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
