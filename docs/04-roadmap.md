# Roadmap De Entrega

Ordem pensada para entregar valor cedo e deixar o risco (visibilidade, captura) coberto por testes antes de dados reais.

| Fase | Entrega | Itens |
|---|---|---|
| 0 | Fundacao: repo, ambiente, CI, Postgres, login, perfis, auditoria, backup | 1, 15 |
| 1 | Usuarios/OAB, motor de visibilidade configuravel + matriz de testes | 1, 2 |
| 2 | Clientes e processos, tela do processo | 4 |
| 3 | Calendario (feriados/suspensoes) e prazos com dupla conferencia | 6 |
| 4 | Agendamentos (7 tipos), agenda individual, conflitos, observacoes, anexos | 5, 7, 8, 10 |
| 5 | Conclusao e conferencia das socias, estagiario, painel das socias | 10, 11 |
| 6 | Captura: DJEN primeiro, depois Domicilio e e-mail; cruzamento e fila "A classificar" | 3 |
| 7 | Alertas e-mail, depois WhatsApp | 9 |
| 8 | Atendimento | 13 |
| 9 | Financeiro | 12 |
| 10 | Relatorios internos (PDF) e relatorio ao cliente | 14 |
| 11 | Biblioteca de modelos, endurecimento LGPD, treinamento | 10, 15 |

Criterio de pronto por fase: regras da spec cobertas por teste, permissoes testadas pela matriz, auditoria registrando, migracao reversivel, documentacao e RAG atualizadas.
Captura (fase 6) depende de verificar as fontes oficiais; fazer uma investigacao tecnica (spike) antes.

## Status (2026-10-19)
Fases 0 a 11 implementadas no backend e commitadas (172 testes, Postgres real validado). Faltam: frontend das telas (o console `/dev/` cobre os testes), conectores do Domicilio e e-mail, WhatsApp, seed da equipe real, deploy das migracoes na VM. Ver `docs/05-pendencias.md` (itens 34 a 42) e `docs/rag/estado.md`.
