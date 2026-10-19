# Decisoes

## 2026-10-19 - Spec funcional adotada como fonte da verdade
Usuaria definiu 15 itens; ajuste: Luana tambem inclui agendamentos nos processos da Andressa (itens 2 e 8). Arquivo: `docs/00-especificacao.md`.

## 2026-10-19 - ADR-0001 Stack: Java 21 + Spring Boot + PostgreSQL + Vue 3/TS
A usuaria recusou a proposta Django+HTMX e pediu Node, Python ou Java (gosta de Java). Escolhido Java: monolito modular Spring Boot 3 (Modulith/ArchUnit), Flyway, Envers, db-scheduler, OpenHTMLtoPDF, front Vue 3 + TypeScript + Vite (PWA). Alternativas: NestJS e Django (descartadas por mais pecas a montar ou menos garantia de tipos; reavaliar se a usuaria preferir). Detalhe e fatos do ambiente: `docs/01-arquitetura.md`. Substitui a proposta Django+HTMX da mesma data. Versoes efetivas (2026-10-19): Spring Boot 4.1.1, Spring Modulith 2.1.1, Postgres 16, Vue 3.5, Vite 8, Vitest 4. Sessao por cookie HttpOnly + CSRF (cookie XSRF-TOKEN) em vez de JWT, por ser SPA de mesma origem atras do nginx. Hash de senha BCrypt(12); Argon2 adiado.

## 2026-10-19 - Projeto local e RAG em duas camadas
Pasta `/home/p_991274/crispim-campos` (nao e repositorio Git ainda). RAG do projeto em `docs/rag/`; ponteiro no RAG global (`/home/p_991274/ai/systems/crispim-campos.md`). Motivo: o conhecimento viaja com o projeto e o global so roteia.

## 2026-10-19 - Site e sistema viram um produto unico
Usuaria quer incorporar o site (`site/`) ao sistema e vice-versa: mesmo repositorio, mesmo deploy, mesma marca. Mapa e proposta em `docs/07-integracao-site.md`. Substitui "site e sistema independentes". Destino do Supabase (`testeLogin`) e escopo do portal do cliente: pendentes em `docs/05-pendencias.md`.

## 2026-10-19 - Regras iniciais de visibilidade por perfil
Contexto: a matriz depende de pessoas, mas o sistema nao pode ter nomes fixos (invariante 10). Decisao: ao criar usuario, `MatrizVisibilidadeService` aplica padrao por perfil (socia ve a si, socias e advogados e e vista por socias; advogado ve so o proprio e e visto pelas socias; assistente e estagiario comecam sem regra) e a socia ajusta pela tela/API. Luana e liberada para Leticia, Lorraynny e Andressa por configuracao, nao por codigo. Estagiario nunca usa a matriz (`PoliticaVisibilidade` ignora regras dele). Consequencia: o seed da equipe real e passo de implantacao, nao de migracao.

## 2026-10-19 - Titular do processo e visibilidade de cliente
Titular do processo para a visibilidade e o advogado responsavel (`advogado_id`, escopo PROCESSO). Recurso fora da visibilidade responde 404 (nao revela existencia, evita IDOR). Cliente nao tem titular: socias veem todos; os demais so veem clientes com ao menos um processo visivel. Escrita de cadastros restrita a socias ate a usuaria decidir a pendencia 4.

## 2026-10-19 - Prazos: fatal informado, calendario que recusa lacuna
Pendencia 1 em aberto: o prazo fatal e informado por quem lanca; so o interno e calculado (fatal menos 3 dias uteis no calendario do tribunal do processo). O calculo recusa periodo sem nenhum feriado nacional cadastrado, para nunca produzir prazo interno errado em silencio. Dupla conferencia vale no dominio, no servico e no banco (CHECK); conferente precisa enxergar o agendamento (escopo AGENDAMENTO) e nao ser estagiario. Atribuicao: socia a qualquer usuario ativo; assistente a si ou estagiario; advogado a si (invariante 7). Feriado e suspensao compartilham a tabela `feriado` (campo `tipo`).

## 2026-10-19 - Agenda: compromissos, conflitos e anexos
Agendamento unico (`agendamento`) serve prazos e compromissos; a data de trabalho na agenda e o prazo interno (prazos) ou a data de `inicio_em` no fuso America/Sao_Paulo (AUD/ATD). Conflito de horario so avisa (pendencia 5): mesmo advogado (AUD/ATD) ou duas audiencias no escritorio; o aviso nao detalha processo. Anexos: dono pode ser agendamento ou processo e herdam a visibilidade do dono; nome no disco e UUID, download sempre `application/octet-stream` + attachment. Observacoes sao historico imutavel (trigger) e nunca entram no relatorio ao cliente.

## 2026-10-19 - Fases 5 a 11 (resumo das decisoes)
- Conferencia: estagiario -> supervisor -> socia; devolucao exige motivo, volta ao responsavel e entra no historico (tabela `conferencia` imutavel). Socia pode conferir o que ela mesma finalizou (sem exigencia de segunda socia; pendencia 36).
- Captura: pipeline idempotente (UNIQUE no banco); filtros por dados; DJEN por API publica; Domicilio e e-mail por `POST /api/captura/importar` ate haver conector. Resposta vazia/fora do padrao da fonte e FALHA registrada, nunca "0 itens".
- Alertas: gerados no banco (idempotentes) e enviados por SMTP quando configurado; sem SMTP ficam PENDENTE (so as do ultimo dia sao enviadas depois). Mensagem so com numero do processo, tipo e data.
- Relatorio ao cliente: conteudo so existe como `ConteudoCliente` (lista branca, teste por reflexao); aprovacao exigida no servico e por CHECK; so apos aprovado ha PDF/envio; ao enviar guarda copia e marca andamentos como repassados. Advogado so gera dos proprios processos; financeiro so se a socia gerar.
- Financeiro: nao usa a matriz de visibilidade; toda operacao exige perfil Socia e a consulta de resumo grava auditoria (por isso nao e `readOnly`).
- Seguranca: limite de login por IP+e-mail em memoria (5 falhas/15 min -> 429); 2FA TOTP opcional por usuario (obrigatoriedade das socias e decisao da usuaria); CSP e Referrer-Policy; `Clock` unico no modulo `seguranca`.
- Jackson 3 (Boot 4) recusa `null` em primitivo: `spring.jackson.deserialization.fail-on-null-for-primitives=false` (booleano ausente = false).
- Ambiente de teste: Postgres embutido so no escopo `test`; o console `/dev/` e o seed ficam em `src/test` e nao entram no pacote de producao.
- Conexao Google por pessoa (2026-10-19, pedido da usuaria): OAuth 2.0 code+PKCE, escopos `openid email gmail.readonly` (sem `gmail.send`), refresh token AES-256-GCM com chave em `GOOGLE_TOKEN_KEY`, uma conexao por usuario, so o proprio usuario le/desconecta, auditoria so de insercao; captura por e-mail (`GmailFonte`) usa o mesmo pipeline com titular = quem conectou. Detalhes e passos no Google Cloud em `docs/13-conexao-google.md`.
