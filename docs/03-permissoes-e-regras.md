# Permissoes E Regras (derivadas da spec)

Fonte: `00-especificacao.md`. Esta e a matriz que vira teste automatizado.

## Visibilidade de publicacoes/agendamentos (seed inicial)
Linha = quem ve; coluna = titular (em cujo nome esta).

| Observador \ Titular | Leticia | Lorraynny | Andressa | Wendell |
|---|---|---|---|---|
| Leticia (socia) | sim (Controladoria) | sim | sim (conferencia) | sim (conferencia) |
| Lorraynny (socia) | sim | sim | sim (conferencia) | sim (conferencia) |
| Andressa | nao | nao | sim | nao |
| Wendell | nao | nao | nao | sim |
| Luana (assistente) | sim | sim | sim | NAO |
| Estagiario | somente o que lhe for atribuido | | | |

Socias editam esta matriz por tela (tabela `RegraVisibilidade`).

## Acoes por perfil
| Acao | Socia | Advogado | Assistente | Estagiario |
|---|---|---|---|---|
| Gerir usuarios/OAB/visibilidade | sim | nao | nao | nao |
| Classificar fila "A classificar" | sim | nao | nao | nao |
| Distribuir tarefas | sim (a Luana, advogados, estagiarios) | nao | nao | nao |
| Criar agendamento no processo | sim | proprios (`Ponto a confirmar`) | sim, nos processos que ve (Leticia, Lorraynny, Andressa), atribuindo a si ou a estagiarios; nunca a advogados/socias | nao |
| Lancar prazo | sim | sim | sim | sim (nao confirma) |
| Conferir (confirmar) prazo | sim, se nao foi quem lancou | sim, se nao foi quem lancou (`Ponto a confirmar`) | sim, se nao foi quem lancou (`Ponto a confirmar`) | NAO |
| Finalizar agendamento | sim | sim | sim | sim, passa antes pelo supervisor |
| Marcar Conferido/Devolvido (socia) | sim | nao | nao | nao |
| Painel das socias | sim | nao | nao | nao |
| Financeiro | sim | nao | nao | nao |
| Relatorio ao cliente (gerar/aprovar) | sim | so dos proprios processos (Wendell, Andressa) / responsavel | nao | nao |

## Fluxos de estado
- Prazo: lancado -> AGUARDANDO_CONFERENCIA_PRAZO -> (outro usuario valida) -> PENDENTE -> FINALIZADO -> EM_CONFERENCIA_SOCIA -> CONFERIDO | DEVOLVIDO (motivo; volta ao responsavel).
- Estagiario: FINALIZADO -> EM_CONFERENCIA_SUPERVISOR -> EM_CONFERENCIA_SOCIA.
- AUD/ATD: sem regra de 3 dias uteis; ATD concluido gera `Atendimento`.
- Relatorio ao cliente: RASCUNHO -> EM_REVISAO -> APROVADO -> ENVIADO.

## Calculos
- Prazo interno = prazo fatal menos 3 dias uteis (calendario do tribunal do processo). AUD/ATD nao usam.
- Agenda: vermelho = vencido ate 30 dias atras; amarelo = hoje; verde = amanha; URGENTE = icone. Cor de status no cartao (fundo/borda), bolinha = tipo. AUD/ATD ordenados por hora.
- Alerta AUD: aparece na agenda 10 dias antes e e enviado 10 dias antes e na vespera.

## Cores dos tipos
CTR preto, INI rosa, CON laranja, MAN cinza, AUD roxo, REC marrom, ATD azul.
