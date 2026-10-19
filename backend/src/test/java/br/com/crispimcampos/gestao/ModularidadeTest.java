package br.com.crispimcampos.gestao;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Falha o build se um modulo violar a fronteira de outro (ciclos, acesso a internos). */
class ModularidadeTest {

	@Test
	void modulosRespeitamSuasFronteiras() {
		ApplicationModules.of(GestaoApplication.class).verify();
	}
}
