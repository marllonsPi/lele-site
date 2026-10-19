# Integracao Site + Sistema

Decisao da usuaria (2026-10-19): site institucional (`site/`) e sistema de gestao passam a ser um unico produto, num unico repositorio. Este documento mapeia o que existe no site e como encaixa; o que e proposta esta marcado `Proposta` e depende de resposta em `docs/05-pendencias.md`.

## O que existe no site (fato, conferido no codigo)
| Parte | Conteudo | Observacao |
|---|---|---|
| Landing | `index.html` + `styles.css` + `assets/` + `fotos/` | HTML/CSS/JS estaticos, sem build; GitHub Pages, dominio `www.crispimcamposadvogados.com.br` (`CNAME`) |
| Triagem | 3 etapas no navegador, abre WhatsApp com a mensagem | Nada persistido; dado potencialmente sensivel sai pelo WhatsApp |
| Agenda | Google Calendar Appointment Scheduling, liberada na sessao apos a triagem | Link e dado de negocio |
| Comentarios | Formulario abre `mailto:` para moderacao manual | Sem backend |
| Area do cliente (`testeLogin/`) | Login Supabase, painel do cliente (caso mais recente) e painel admin (CRUD de casos e andamentos) | Experimental; schema Supabase incompleto (`cases`/`case_updates` nao versionadas); contas de teste no SQL |
| Equipe | Letícia (OAB/DF 84.089), Lorraynny (OAB/DF 74.371), Andressa (OAB/DF 83.736) | Texto/OAB so mudam com confirmacao |

## Sobreposicao com o sistema
- `testeLogin` admin (casos + linha do tempo) = Fase 2 (clientes/processos) e andamentos do sistema; `profiles.role` client/admin = `usuario`/perfis do sistema. Duas fontes de verdade para caso/usuario nao devem coexistir.
- Login de cliente nao existe no sistema (so equipe). Cliente enxergar o proprio caso e requisito novo: precisa entrar na spec e respeitar o invariante 6 (nada interno ao cliente) e o 9 (LGPD).

## Proposta de arquitetura unificada
1. Um repositorio; `site/` passa a ser `site/` (renomear so no commit de integracao, preservando conteudo).
2. Mesmo `docker compose`: nginx serve o site estatico em `/` e o app Vue em `/app`; `/api` segue para o backend. Dominio publico unico.
3. Triagem: alem do WhatsApp, enviar a triagem ao backend como "contato/lead" (novo modulo `captacao`, com consentimento LGPD), que alimenta Atendimento (item 13 da spec). Mantem WhatsApp como canal.
4. Area do cliente: portal proprio no sistema (login de cliente, so leitura do proprio processo, lista branca de campos como o relatorio ao cliente) substitui `testeLogin`/Supabase; migracao dos dados so com autorizacao.
5. Comentarios: moderacao pelas socias no sistema (fila) em vez de `mailto:`.
6. Agenda publica: manter Google Calendar ate decisao; depois, agenda propria ligada ao tipo ATD.
7. Marca (dourado, Cormorant Garamond/Sora) e tom editorial do site viram o tema do app.

## Ordem sugerida
Fase 0 (agora): consolidar o site no repo e servir no Compose. Antes da Fase 2: decidir destino do Supabase. Captacao e portal do cliente entram no roadmap apos Fase 2 (precisam de clientes/processos).

## Guardrails herdados do site (continuam valendo)
Nao alterar telefones, e-mails, OABs, dominio, textos juridicos, links de agenda sem confirmacao; nunca `service_role` no frontend; sem SQL no Supabase, publicacao no Pages ou DNS sem autorizacao; sem depoimento/resultado inventado; sem dado de cliente em exemplo.
