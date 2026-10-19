package br.com.crispimcampos.gestao;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Sobe o contexto completo com Postgres real (migracoes Flyway + validacao do mapeamento JPA). Ignorado sem Docker. */
@Testcontainers(disabledWithoutDocker = true)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GestaoApplicationTests {

	@Test
	void contextLoads() {
	}

}
