# RAG Do Sistema Crispim & Campos

Memoria do projeto para qualquer IA. Fonte da verdade funcional: `docs/00-especificacao.md`. Ordem de confianca: codigo atual > este arquivo e `docs/` > RAG global (`/home/p_991274/ai`, card `systems/crispim-campos.md`) > conhecimento geral.

## Pasta do ambiente Sistema
Esta pasta (`crispim-campos`) e o ambiente "Sistema" do escritorio. Alem do sistema (`backend/`, `frontend/`, `docs/`), contem o site institucional do mesmo escritorio em `lele-site-main/` ("lele-site"), que tem RAG propria: ler `lele-site-main/AGENTS.md` para qualquer tarefa do site. Decisao 2026-10-19: site e sistema sao um produto so (ver `docs/07-integracao-site.md`), mas as stacks seguem distintas (site estatico, sistema Java/Vue): nao aplicar stack/regras de um ao outro ate a integracao ser feita.

## Carregar so o necessario
| Tarefa | Ler |
|---|---|
| Qualquer | este indice + `invariantes.md` |
| Regra de negocio/permissao | `docs/00-especificacao.md` (item citado), `docs/03-permissoes-e-regras.md` |
| Estrutura/stack | `docs/01-arquitetura.md`, `decisions.md` |
| Banco | `docs/02-modelo-de-dados.md` (e as migracoes reais quando existirem) |
| O que falta decidir | `docs/05-pendencias.md` |
| Como construir/revisar/testar | `docs/06-padroes-engenharia.md` |
| Site, portal do cliente, integracao | `docs/07-integracao-site.md`, `lele-site-main/AGENTS.md` |
| O que fazer a seguir | `docs/04-roadmap.md`, `estado.md` |

## Arquivos
| Arquivo | Conteudo |
|---|---|
| `invariantes.md` | Regras que nunca podem ser quebradas |
| `decisions.md` | Decisoes tomadas (ADR curto, com data) |
| `estado.md` | Estado vigente (fase, o que existe, proximo passo) |
| `licoes.md` | Erros e armadilhas para nao repetir |

## Protocolo de manutencao (cuidar para sempre)
1. Antes de mexer: ler indice + invariantes; abrir codigo real antes de decidir.
2. Ao terminar qualquer entrega relevante: atualizar `estado.md`; decisao nova vai para `decisions.md`; erro novo para `licoes.md`; mudou regra do cliente: atualizar `docs/00-especificacao.md` (com data) e `docs/03`.
3. Fato novo substitui o obsoleto no lugar; sem narrativa de sessao, sem hash de build, sem segredo, sem dado real de cliente/processo.
4. Divergencia codigo x docs: preservar o codigo, relatar, corrigir o doc.
5. Rodar `tools/rag-check.sh` antes de fechar a tarefa (0 erros).
6. Nunca inventar regra, campo, endpoint ou fonte externa: ausencia vira item em `docs/05-pendencias.md`.
