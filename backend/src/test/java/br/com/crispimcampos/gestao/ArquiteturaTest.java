package br.com.crispimcampos.gestao;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packagesOf = GestaoApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

	/**
	 * Controller nunca le repositorio direto: leitura de dado protegido passa pelo servico
	 * do modulo (e, a partir da Fase 1, pelo filtro de visibilidade de `acesso`).
	 */
	@ArchTest
	static final ArchRule controllerNaoUsaRepositorio = noClasses()
			.that().haveSimpleNameEndingWith("Controller")
			.should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
}
