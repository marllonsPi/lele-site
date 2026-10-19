package br.com.crispimcampos.gestao.dev;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;

/** Postgres real, sem Docker nem root, so para desenvolvimento e testes. Nunca entra no pacote de producao. */
public final class PostgresEmbutido {

	private PostgresEmbutido() {
	}

	/** Dados persistidos em {@code dir}; reiniciar mantem o que foi cadastrado. */
	public static EmbeddedPostgres iniciar(int porta, Path dir) throws IOException {
		EmbeddedPostgres pg = EmbeddedPostgres.builder().setPort(porta).setDataDirectory(dir).setCleanDataDirectory(false).start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				pg.close();
			} catch (IOException e) {
				// encerrando
			}
		}));
		return pg;
	}

	public static String url(int porta) {
		return "jdbc:postgresql://localhost:" + porta + "/postgres";
	}
}
