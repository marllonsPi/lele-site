# Padroes De Engenharia

Como o projeto e construido e mantido. Vale para humanos e IAs.

## Estrutura do repositorio (monorepo, Git)
```
backend/    Spring Boot (Maven), pacotes por funcionalidade: contas, acesso, cadastros, agenda, calendario, captura, alertas, painel, financeiro, atendimento, relatorios, modelos, auditoria
frontend/   Vue 3 + TypeScript + Vite
deploy/     Docker Compose, proxy, scripts de backup/restauracao
docs/       especificacao, arquitetura, ADRs, RAG
tools/      rag-check.sh e utilitarios
```
Dentro de cada modulo: `api` (controllers/DTOs), `dominio` (regras, sem dependencia de framework web), `infra` (JPA, clientes externos). Conectores de captura implementam uma interface unica (`FonteDePublicacoes`).

## Git e revisao
- Branch principal protegida; trabalho em branches curtas `feat/…`, `fix/…`, `docs/…`; merge por pull request com CI verde.
- Commits no formato Conventional Commits (`feat(agenda): …`), em portugues, uma mudanca logica por commit.
- Push e deploy so quando a usuaria pedir.
- Nenhum segredo, dado real de cliente ou processo no Git (hook de pre-commit com varredura de segredos).

## Definicao de pronto (por entrega)
1. Regras da spec cobertas por teste; permissoes cobertas pela matriz de `docs/03`.
2. Migracao Flyway versionada, idempotente por versao e compativel com a versao anterior (aditiva).
3. Auditoria registrando a acao; mensagens e telas em portugues; layout testado em celular e desktop.
4. Build, testes, lint e analise de seguranca verdes; nunca declarar teste passando sem executar.
5. `docs/` e `docs/rag/` atualizados; `tools/rag-check.sh` com 0 erros.

## Testes
- Unidade para regras (calculo de dia util, conflitos, estados, visibilidade).
- Integracao com Postgres real (Testcontainers) para consultas, constraints e migracoes.
- ArchUnit: nenhum modulo le dados protegidos sem passar por `acesso`; dominio sem dependencia de web.
- Contrato: conectores de captura testados com respostas gravadas e anonimizadas (nunca dado real).
- E2E (Playwright) dos fluxos criticos: login, classificar publicacao, lancar/conferir prazo, relatorio ao cliente.

## Seguranca e LGPD (checklist de revisao)
Autorizacao no servidor em toda rota; sem IDOR (checar posse/visibilidade do recurso, nao so o perfil); validacao de entrada; upload com tipo/tamanho/antivirus; cabecalhos de seguranca; senha e 2FA; limite de tentativas; log sem dado pessoal; dependencias auditadas; backups criptografados com teste de restauracao; minimo de dados pessoais.

## Decisoes e documentacao
- Decisao arquitetural nova = ADR curto em `docs/rag/decisions.md` (contexto, decisao, alternativas, consequencias, data).
- Regra de negocio nova = primeiro em `docs/00-especificacao.md` com data, depois codigo e teste.

## Operacao
Ambientes local/homologacao (dados ficticios)/producao; migracao com backup previo; rollback documentado; monitor de falha da captura diaria, dos alertas e do backup (falha silenciosa e o pior caso).
