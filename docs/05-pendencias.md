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

## Fase 4 (2026-10-19)
29. Anexos sem antivirus: hoje so extensao + assinatura do arquivo e 20 MB. Decidir ferramenta (ex.: ClamAV) antes de dados reais.
30. `STORAGE_DIR` precisa ser volume persistente no Compose da VM e entrar no backup criptografado (hoje o backup cobre so o Postgres).
31. Audiencia sem duracao informada assume 60 min para detectar conflito. Confirmar o padrao.
32. Luana nao pode agendar ATD (o atendente seria advogado/socia, vedado pelo item 8) nem AUD para advogado; so para si ou estagiario. Confirmar se deseja excecao para agendar atendimentos em nome de advogados.
33. Itens vencidos ha mais de 30 dias saem da agenda individual (continuam nos dados); ver pendencia 2 sobre onde passam a aparecer.

## Fases 5 a 11 (2026-10-19)
34. Domicilio Judicial Eletronico: depende de acesso/credenciais (spike da pendencia 12); hoje entra por `POST /api/captura/importar`. DJEN tem conector. E-mail: conector `GmailFonte` por OAuth (docs/13) implementado; faltam o projeto no Google Cloud (usuaria), validar a consulta/parser com e-mails push reais, escolher modo (teste com reconexao semanal x Workspace interno x producao nao verificada) e a tela de conexao no frontend.
35. WhatsApp: sem provedor definido (pendencia 13); relatorio ao cliente por WhatsApp/impressao e so registrado (o escritorio entrega o PDF). Alertas so por e-mail.
36. Socia pode conferir o que ela mesma finalizou. Exigir outra socia? E devolucao: pode a socia que devolveu receber de volta para conferir de novo?
37. 2FA existe e e opcional; definir se e obrigatorio para socias (pendencia 14) e se o segredo TOTP deve ser criptografado em repouso.
38. Seed da equipe real: Luana liberada pela matriz, filtro de TRT da Leticia, OABs suplementares, feriados locais e suspensoes dos tribunais (pendencias 9 e 11). Timbre: logotipo do site; falta endereco e contato do escritorio (`app.escritorio.*`, pendencia 10).
39. Alertas e captura agendados assumem UMA instancia do backend (sem trava distribuida); limite de login tambem e em memoria.
40. Backup do Postgres existe; falta incluir `STORAGE_DIR` (anexos e modelos) e testar restauracao (pendencia 30).
41. Relatorio ao cliente: o texto das movimentacoes vem do resumo da publicacao e precisa de revisao do advogado (linguagem simples). Confirmar se andamentos tambem devem nascer de movimentos que nao sejam publicacoes (pendencia 8).
42. Anonimizacao LGPD preserva processos, relatorios enviados e auditoria (retencao legal). Confirmar prazos de retencao (pendencia 16).

## Importacao e honorarios (2026-10-21)
43. A planilha financeira nao tem CPF/CNPJ, data nem forma de pagamento das parcelas pagas: clientes casam por nome (a conferir) e parcelas pagas ficam sem data. Decidir se a planilha passa a ter colunas de CPF/CNPJ, data e forma de pagamento.
44. Estrutura real das pastas do Drive e dos modelos de contrato nao foi vista: o extrator assume uma pasta por cliente na raiz configurada (`IMPORTACAO_PASTA_CLIENTES_ID`) e contratos/procuracoes em PDF com texto, DOCX ou TXT. Rodar com 2 ou 3 clientes reais, conferir a previa e ajustar os padroes (CPF, RG, endereco, valor, parcelas, assinatura).
45. Drive exige projeto no Google Cloud com a API do Drive ativada e o escopo `drive.readonly` (restrito) na tela de consentimento, alem de `GOOGLE_*` (docs/13). Cada socia conecta "Google com Drive" em `/importacao`. O retorno do consentimento ainda vai para `/configuracoes?google=...` (rota redireciona para `/importacao`); conferir o prefixo `/app/` no layout unificado.
46. Sincronizacao diaria desligada: ligar com `IMPORTACAO_SINCRONIZACAO_DIARIA=true`, `IMPORTACAO_CONTA_USUARIO_ID`, `IMPORTACAO_PLANILHA_ID`, `IMPORTACAO_PASTA_CLIENTES_ID`. Confirmar se o que ela aplica sozinha (parcelas, despesas, documentos novos sem divergencia) e aceitavel ou se tudo deve esperar confirmacao.
47. Lancamentos "EXTRA - ..." da planilha (honorarios avulsos sem cliente) e despesas do escritorio: extras sao ignorados com motivo (lancar manualmente com o cliente); despesas vao para `despesa_escritorio`. Confirmar o destino desejado.
48. Importacao nao gera parcelas a partir do texto do contrato do Drive (so valor, numero de parcelas, forma e assinatura); parcelas vem da planilha ou do lancamento manual.
49. Areas: "Saude" e "Bancario" foram mapeadas para Civel; "Regularizacao" para Administrativo. Confirmar o mapeamento de tipo de acao -> area (`AreaPorTexto`); o que nao casa pede escolha na previa.
50. Tabela OAB/SP: a planilha enviada tem so a OAB/DF. Enquanto nao houver tabela da OAB/SP, a sugestao para SP aparece sem piso (com aviso). Enviar a planilha da OAB/SP (mesmo formato) e importar com seccional SP.
51. Fases (judicial/extrajudicial/recurso/cumprimento) foram inferidas do texto do servico; revisar na tela Honorarios. Servicos com area equivalente inexistente (ex.: Consultoria Preventiva) entram sem area ate as socias definirem.
52. Politica comercial lida da planilha: fator 1,2 sobre o minimo, desconto 10% (5% trabalhista), desconto por pacote 0% ate as socias definirem. A planilha tambem fala em exito trabalhista de 35% a 40% (aviso: abaixo do minimo e vedado, salvo exito).
53. Modelo oficial do contrato de honorarios nao foi enviado: vale um modelo-padrao generico (editavel em Honorarios > Modelo do contrato). Trocar pelo texto do escritorio e conferir os campos.
54. Contrato lancado por socia ao "assinar": advogado nao lanca no financeiro (invariante 1). Confirmar se deseja que o advogado registre a assinatura (a socia confirmaria depois).
55. "Reunioes realizadas" = atendimentos concluidos (registro `atendimento` de origem AGENDAMENTO); "contratos fechados" = propostas com valor fechado. Confirmar a definicao de conversao.
56. Pro bono/permuta (valor fechado zero) nao lanca parcelas; registrar permuta como pagamento em bens fica fora do sistema.
