# Decisoes

## 2026-10-19 - Spec funcional adotada como fonte da verdade
Usuaria definiu 15 itens; ajuste: Luana tambem inclui agendamentos nos processos da Andressa (itens 2 e 8). Arquivo: `docs/00-especificacao.md`.

## 2026-10-19 - ADR-0001 Stack: Java 21 + Spring Boot + PostgreSQL + Vue 3/TS
A usuaria recusou a proposta Django+HTMX e pediu Node, Python ou Java (gosta de Java). Escolhido Java: monolito modular Spring Boot 3 (Modulith/ArchUnit), Flyway, Envers, db-scheduler, OpenHTMLtoPDF, front Vue 3 + TypeScript + Vite (PWA). Alternativas: NestJS e Django (descartadas por mais pecas a montar ou menos garantia de tipos; reavaliar se a usuaria preferir). Detalhe e fatos do ambiente: `docs/01-arquitetura.md`. Substitui a proposta Django+HTMX da mesma data. Versoes efetivas (2026-10-19): Spring Boot 4.1.1, Spring Modulith 2.1.1, Postgres 16, Vue 3.5, Vite 8, Vitest 4. Sessao por cookie HttpOnly + CSRF (cookie XSRF-TOKEN) em vez de JWT, por ser SPA de mesma origem atras do nginx. Hash de senha BCrypt(12); Argon2 adiado.

## 2026-10-19 - Projeto local e RAG em duas camadas
Pasta `/home/p_991274/crispim-campos` (nao e repositorio Git ainda). RAG do projeto em `docs/rag/`; ponteiro no RAG global (`/home/p_991274/ai/systems/crispim-campos.md`). Motivo: o conhecimento viaja com o projeto e o global so roteia.

## 2026-10-19 - Site e sistema viram um produto unico
Usuaria quer incorporar o site (`lele-site-main/`) ao sistema e vice-versa: mesmo repositorio, mesmo deploy, mesma marca. Mapa e proposta em `docs/07-integracao-site.md`. Substitui "site e sistema independentes". Destino do Supabase (`testeLogin`) e escopo do portal do cliente: pendentes em `docs/05-pendencias.md`.
