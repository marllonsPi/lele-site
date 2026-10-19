# Guia de teste (modo desenvolvimento)

Tudo aqui usa **dados ficticios** e um Postgres embutido; nada toca producao, e-mail real, DJEN real ou WhatsApp.

## Subir
```bash
export JAVA_HOME=<caminho do JDK 21>
cd backend
./mvnw spring-boot:test-run -Dspring-boot.run.main-class=br.com.crispimcampos.gestao.dev.DevApplication
```
Abra `http://localhost:8080/dev/index.html`. Para recomecar do zero, pare o processo e apague `backend/target/dev-pg` e `backend/target/dev-storage`.
Usuarios (senha de todos e `senha-de-teste-123`): Socia A e B, Advogada C (como Andressa), Advogado D (como Wendell), Assistente E (como Luana), Estagiario F (supervisora: Advogada C). Em dev os e-mails sao apenas registrados no log do servidor.

## Roteiro por item da especificacao
| Item | O que fazer | Resultado esperado |
|---|---|---|
| 2 Visibilidade | Entre como Advogado D, depois Assistente E; veja `processos`, `publicacoes`, `agenda` | D so ve o proprio; E ve A, B e C mas nunca D |
| 3 Captura | Socia A em `publicacoes` | Cruzamento DJEN+e-mail, "so em uma fonte", sugestao de cadastro, item do TRT-1 no log de ignorados (TRT-10 mantido) |
| 3 Classificar | Classificar uma publicacao | Vira prazo "aguardando conferencia"; processo vinculado; andamento pendente em `atendimento` |
| 5 Conflito | Aba `console`, preset "Marcar audiencia": horario 10:15 amanha | Resposta traz aviso de conflito (audiencias no escritorio); nao bloqueia |
| 6 Dupla conferencia | `prazos`: Assistente E lancou um prazo; tente Conferir como E, depois como Socia B | E nao confirma o proprio; B confirma e o status passa a PENDENTE |
| 7 Agenda | `agenda` como Advogada C | Cores (vermelho/amarelo/verde), bolinha do tipo, URGENTE, alerta de audiencia, compromissos por horario |
| 10 Conferencia | Estagiario F: Finalizar; Advogada C: `conferencia` aprova; Socia A: Conferido ou Devolvido (motivo) | Fluxo estagiario -> supervisor -> socia; devolvido volta a agenda do responsavel |
| 11 Painel | Socia A em `painel` | Carga por pessoa, atrasos, semana, compromissos, aguardando conferencia, fila A classificar |
| 9 Alertas | Socia A em `alertas`: "Gerar e enviar" | Alertas de vespera/10 dias/resumo; segunda execucao nao duplica |
| 12 Financeiro | Socia A em `financeiro` (cliente 1); depois como Advogada C | Resumo e parcelas; para C, HTTP 403 |
| 13 Atendimento | Advogada C: Finalizar o atendimento (ATD) de hoje | Cria o registro de atendimento; `Marcar repassado` nos andamentos |
| 14.1 Relatorios | `relatorios`: tipo, Ver, Abrir PDF | Tabela e PDF com timbre; "Enviar as socias" simula e-mail |
| 14.2 Ao cliente | `relatorios`: Gerar rascunho (cliente 1), editar comentario, Aprovar, Abrir PDF, Enviar | PDF so depois de aprovado; envio marca andamentos como repassados; nada interno aparece |
| 10 Modelos | `modelos` | Lista por tipo/area; so socias cadastram |
| 15 Seguranca | `seguranca`: Iniciar, Confirmar, depois sair e entrar de novo | Login passa a pedir o codigo; 5 senhas erradas seguidas dao HTTP 429 |
| 15 LGPD | `console`, preset "LGPD: exportar cliente 1" | JSON com os dados do cliente |

## Testes automatizados
`cd backend && ./mvnw test` (172 testes; 1 ignorado exige Docker). `PostgresRealTest` valida o sistema num Postgres real embutido.
