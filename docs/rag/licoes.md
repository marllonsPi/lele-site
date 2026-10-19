# Licoes

## Maven da maquina aponta para mirror institucional (2026-10-19)
`~/.m2/settings.xml` redireciona `central` para um Nexus interno da Camara. Este projeto e particular e o CI nao enxerga essa rede: `backend/.mvn/maven.config` aponta para `backend/.mvn/settings.xml` (vazio, Central direto). Rodar sempre de dentro de `backend/`. Evidencia: build com `./mvnw` resolve do Central.

## `java` padrao e o 1.8 (2026-10-19)
Spring Boot 4 exige Java 17+. Exportar `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` antes de `./mvnw`.

## Initializr devolveu versao inexistente (2026-10-19)
start.spring.io gerou `4.1.1.RELEASE`, que nao existe no Maven Central (a versao e `4.1.1`). Conferir toda versao gerada no repositorio real (`maven-metadata.xml`).

## npm 10.8 quebra no resolver de peer deps (2026-10-19)
`npm install` falha com `Cannot read properties of null (reading 'edgesOut')`. Mitigacao: `frontend/.npmrc` com `legacy-peer-deps=true`. CI/Docker usam Node 22.

## Teste de contexto exige Docker (2026-10-19)
`GestaoApplicationTests` usa `@Testcontainers(disabledWithoutDocker = true)`: sem Docker ele e IGNORADO, nao aprovado. Nao declarar a Flyway/JPA validada sem rodar esse teste.
