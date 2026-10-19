package br.com.crispimcampos.gestao.dev;

import br.com.crispimcampos.gestao.GestaoApplication;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * Sobe o sistema inteiro com Postgres embutido, dados ficticios e o console de testes em /dev/index.html.
 * Uso: ./mvnw spring-boot:test-run -Dspring-boot.run.main-class=br.com.crispimcampos.gestao.dev.DevApplication
 */
public class DevApplication {

	static final int PORTA_PG = 54329;

	public static void main(String[] args) throws Exception {
		PostgresEmbutido.iniciar(PORTA_PG, Path.of("target/dev-pg"));
		// O application.yml resolve ${DB_URL:...} etc.; propriedades de sistema vencem os padroes dele.
		System.setProperty("DB_URL", PostgresEmbutido.url(PORTA_PG));
		System.setProperty("DB_USER", "postgres");
		System.setProperty("DB_PASSWORD", "postgres");
		System.setProperty("COOKIE_SECURE", "false");
		System.setProperty("STORAGE_DIR", "target/dev-storage");
		SpringApplication app = new SpringApplication(GestaoApplication.class);
		app.setAdditionalProfiles("dev");
		app.run(args);
	}
}
