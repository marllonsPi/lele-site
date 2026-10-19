package br.com.crispimcampos.gestao;

import org.springframework.boot.SpringApplication;

public class TestGestaoApplication {

	public static void main(String[] args) {
		SpringApplication.from(GestaoApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
