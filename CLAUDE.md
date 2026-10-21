# Crispim & Campos - Sistema de gestao do escritorio

Antes de qualquer tarefa neste projeto: ler `docs/rag/index.md` e `docs/rag/invariantes.md`. Fonte funcional: `docs/00-especificacao.md`. Ao fechar a tarefa, seguir o protocolo de manutencao do indice e rodar `tools/rag-check.sh`.
Build local: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`; backend `cd backend && ./mvnw verify`; frontend `cd frontend && npm test && npm run build`; teste a mao com dados ficticios (sem Docker): `docs/11-guia-de-teste.md`; stack completa `cd deploy && docker compose up --build` (precisa Docker e `.env`).
RAG particular do Marllons: repositorio privado `marllonsPi/ai` (clonar ao lado deste projeto; `kernel.md`, `router.md`, card `projects/crispim-campos.md`). Continuar em outra maquina: `docs/rag/continuar.md`.
