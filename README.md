# Crispim & Campos Advocacia - Sistema De Gestao

Status: backend das Fases 0 a 11 implementado (ver `docs/04-roadmap.md` e `docs/rag/estado.md`); frontend so com login. Para testar a mao: `docs/11-guia-de-teste.md`.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
(cd backend && ./mvnw verify)            # testes do backend
(cd frontend && npm install && npm test && npm run build)
# stack completa (precisa Docker): cp deploy/.env.example deploy/.env && cd deploy && docker compose up --build
```

Pastas: `backend/` (Spring Boot), `frontend/` (Vue 3 + TS), `deploy/` (Compose, backup), `docs/`, `tools/`.

- `docs/00-especificacao.md`: o que o sistema deve fazer (fonte da verdade)
- `docs/01-arquitetura.md`: stack, modulos, captura, seguranca
- `docs/02-modelo-de-dados.md`: entidades
- `docs/03-permissoes-e-regras.md`: matriz de visibilidade e acoes
- `docs/04-roadmap.md`: fases
- `docs/05-pendencias.md`: o que precisa de decisao
- `docs/06-padroes-engenharia.md`: estrutura, Git, testes, seguranca, definicao de pronto
- `docs/10-lgpd.md`: dados, controles e direitos do titular
- `docs/11-guia-de-teste.md`: como subir e testar com dados ficticios
- `docs/rag/`: memoria para IA (como manter tudo coerente)
