# Estado Vigente

- Fase: 0 (fundacao em andamento). Atualizado em 2026-10-19.
- Existe (branch `feat/fase-0-esqueleto`, sem commit ainda): backend Spring Boot 4.1.1/Java 21 com Flyway V1 (usuario, oab, log_auditoria imutavel), login por sessao + CSRF (`/api/login`, `/api/logout`, `/api/csrf`, `/api/me`), auditoria de login, bootstrap da primeira socia por variaveis de ambiente, testes de modularidade/arquitetura/seguranca; frontend Vue 3 + TS + Vite com login e tela inicial; `deploy/` (Compose com Postgres, Dockerfiles, nginx, backup criptografado); CI em `.github/workflows/ci.yml`.
- Validado em 2026-10-19: `./mvnw test` 6 passam, 1 ignorado (contexto com Postgres exige Docker, ausente aqui); frontend `npm run build` e `npm test` (3 testes) ok. NAO validado: Docker/Compose, Flyway contra Postgres real, login ponta a ponta, CI.
- Faltam na Fase 0: 2FA, tela/API de gestao de usuarios e OABs (Fase 1), teste de restauracao do backup.
- Proximo passo: commitar, rodar a stack via Compose onde houver Docker, depois Fase 1 (usuarios/OAB + motor de visibilidade com matriz de testes).
