# Pontos A Confirmar Com A Usuaria

Lacunas da spec e decisoes abertas. Nao decidir por analogia: perguntar. Ao resolver, mover para `docs/rag/decisions.md` e atualizar `00-especificacao.md` se mudar regra.

## Regra de negocio
1. Prazo fatal e digitado ou calculado a partir de inicio + numero de dias? (hoje a spec calcula so o interno)
2. Itens vencidos ha mais de 30 dias: continuam visiveis? em que cor?
3. Quem pode conferir prazo alem das socias (advogados, Luana)? Estagiario nao pode.
4. Advogados (Andressa, Wendell) podem criar agendamentos nos proprios processos?
5. Conflito de agenda: apenas aviso ou bloqueio?
6. "Em nome da Leticia vao para a Controladoria": Controladoria e uma tela/fila propria ou o tipo CTR?
7. Publicacao sem OAB identificavel ou de OAB de advogado desativado: quem recebe?
8. Atendimento avulso e "andamentos pendentes de repasse": o que gera um andamento (so publicacoes classificadas ou tambem movimentos)?

## Dados
9. Dados completos da Luana e OABs suplementares (Leticia/SP, Wendell/GO).
10. Timbre, logotipo e dados do escritorio para PDF.
11. Lista inicial de feriados locais e tribunais usados (DF, SP, GO, TRT-10, outros).

## Tecnico / contratacao
12. Acesso programatico ao DJEN, Domicilio Judicial Eletronico e formato dos e-mails push: verificar fontes oficiais (spike).
13. Provedor de WhatsApp e de e-mail transacional.
14. Hospedagem (pais, provedor), dominio, 2FA obrigatorio.
15. Stack decidida em 2026-10-19: Java 21 + Spring Boot + PostgreSQL + Vue 3/TS (ADR-0001). Esqueleto da Fase 0 criado. Pendente: biblioteca de componentes Vue, hospedagem, onde fica o remoto Git/CI (workflow escrito para GitHub Actions).
16. Volume de dados esperado e retencao (LGPD).

## Integracao site + sistema (2026-10-19)
17. Destino do remoto Git: o remoto `github.com/marllonsPi/lele-site` e publico e e o do site; o sistema vai para ele (monorepo) ou para repositorio novo privado? (nao enviar nada antes de decidir)
18. `testeLogin`/Supabase: descartar, migrar para o Postgres do sistema ou manter ate o portal novo? Ha clientes reais nele?
19. Portal do cliente: o que o cliente ve (so andamentos aprovados? documentos?) e como se autentica.
20. Triagem do site deve gravar no sistema (lead/atendimento) com consentimento LGPD, mantendo o WhatsApp?
21. Comentarios do site: moderacao dentro do sistema?
22. Agenda publica: manter Google Calendar ou agenda propria?
23. Contas de teste e IDs em `testeLogin/supabase/admin-setup.sql`: remover antes de versionar?

## Fase 2 (2026-10-19)
24. Hoje so socias criam/editam cliente e processo (pendencia 4 segue aberta para advogados e Luana). Luana ve processos pela matriz, mas nao os cadastra.
25. CPF/CNPJ guardado em texto puro no banco; decidir criptografia em repouso (arquitetura 9, LGPD).

## Fase 3 (2026-10-19)
26. Seed do calendario cobre so feriados nacionais 2026-2027 (datas fixas + Sexta-feira Santa) e a suspensao de 20/12 a 20/01; faltam feriados locais, recessos e suspensoes de tribunais (pendencia 11). Fora desses anos o calculo recusa (de proposito).
27. Processo ainda nao guarda UF/municipio: feriados ESTADO/MUNICIPIO so valem quando o contexto os informar; hoje o calendario do prazo usa o tribunal.
28. Estagiario lanca prazo so em tarefa distribuida (Fase 5); hoje nao lanca. Advogado lanca prazo apenas atribuido a si mesmo.
