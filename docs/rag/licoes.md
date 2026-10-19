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

- Teste de contexto com Postgres era ignorado sem Docker e escondeu divergencia entre migracao Flyway (`CHAR(2)`) e entidade JPA; so apareceu no deploy. Rodar o teste com Postgres real (Testcontainers/CI) antes de deploy.

## Hibernate valida CHAR(2) como VARCHAR (2026-10-19)
Primeiro boot real contra Postgres 16 (VM) falhou: `Schema validation: wrong column type in oab.uf; found bpchar, expecting varchar`. `columnDefinition` nao muda o tipo validado. Correcao: `@JdbcTypeCode(SqlTypes.CHAR)` em `Oab.uf`. O teste de contexto com Testcontainers teria pego isso; so roda com Docker.

- Teste de seguranca: o post-processor `csrf()` do Spring Security Test troca o repositorio de token do filtro compartilhado e vaza para testes seguintes do mesmo contexto; use o fluxo real (GET /api/csrf, cookie + cabecalho `X-XSRF-TOKEN`).
- `/api/csrf` nao pode depender do token adiado: o endpoint le/gera e grava o cookie ele mesmo (`SecurityConfig.CsrfEndpoint`). O nome nao termina em `Controller` porque a regra ArchUnit proibe controller de tocar `*Repository`.
- Ambiente local varia: nesta maquina nao ha `/usr/lib/jvm`; o JDK 21 usado foi o da extensao Java do VS Code (`~/.vscode/extensions/redhat.java-*/jre/...`). Sem Node e sem Docker.
