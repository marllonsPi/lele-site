# Plano de deploy do sistema completo (Fases 0 a 11) na VM `crispim-micro`

Status: PLANO, nada foi enviado nem executado na VM. Complementa `docs/09-plano-deploy-oracle.md` e `docs/08-infra-oracle.md`. Cada passo so avanca com o OK da usuaria. A VM do Divas Nete nunca e tocada.

## O que mudou desde o plano anterior (precisa constar no deploy)
1. **Migracoes V2 a V15** (a VM esta em V1). Todas aditivas; validadas num Postgres 14 real, NAO no 16 do Compose.
2. **Anexos e modelos** gravam em `STORAGE_DIR`. Sem ajuste o container (usuario nao-root, sem pasta gravavel) falharia no primeiro upload. Preparado localmente: `backend/Dockerfile` (pasta `/data/storage`) e `deploy/docker-compose.yml` (volume `storage`).
3. **Build leve**: o Dockerfile agora usa `-Dmaven.test.skip=true`, para nao baixar o Postgres embutido dos testes numa VM de 1 GB. O JAR de producao foi conferido: 68 MB, sem classes `dev` e sem o console `/dev/`.
4. **Backup** passou a incluir o volume `storage` (`deploy/backup.sh`, `deploy/restore.md`), tambem criptografado.
5. **Novas variaveis** (`deploy/.env.example`): `APP_CAPTURA_AGENDADA` e `APP_ALERTAS_AGENDADO` (ambas `false` ate o e-mail e as OABs estarem configurados), `APP_ESCRITORIO_*` (timbre). SMTP fica para quando houver provedor; nao declarar `SPRING_MAIL_HOST` vazio (a presenca liga o envio).
6. Memoria: PDFs (PDFBox) e Hibernate pesam mais; manter `-Xmx256m`, acompanhar `docker stats`/swap.

## Pontos que precisam da sua atencao antes
- ~~Site publico pode estar fora do ar~~ (hipotese descartada na Fase A: o Caddy esta no ar e o site responde com HTTPS, ver resultado abaixo).
- **O deploy entrega API + login, nao telas.** O frontend de producao so tem login; o console `/dev/` e as telas de teste ficam so em `src/test`. Subir na VM valida infraestrutura (migracoes, HTTPS, backup), mas a equipe nao consegue trabalhar ate existirem as telas. Recomendacao: nao expor com dados reais antes das telas.
- **Alteracoes locais nao commitadas** em `deploy/` e `frontend/` (Caddyfile, nginx, web.Dockerfile, remocao de `frontend/Dockerfile`; eram suas) mais os ajustes acima. Sugiro commitar tudo junto antes do deploy.
- Dados reais so depois de HTTPS ativo, backup restaurado com sucesso e revisao LGPD (`docs/10-lgpd.md`).

## Fases (cada uma pede OK)
**A. Leitura da VM (somente leitura; precisa da chave `~/.ssh/crispim_ed25519`, ja existe nesta maquina):** `uname -m; free -h; swapon --show; df -h; docker compose -p crispim ps; docker logs` do backend; `select version from flyway_schema_history`; portas 80/443; `curl -I` publico e `dig` do dominio. Resultado: situacao real do site e do banco.

**B. Backup antes de mexer:** `pg_dump` criptografado do banco atual (hoje deve estar vazio) e copia do `.env`. Sem isso nao passamos.

**C. Enviar e construir (aditivo, so loopback):** tar do codigo via SSH excluindo `.env`, `node_modules`, `target`, `dist`; `docker compose -p crispim build backend` e depois `web`, em sequencia (RAM); `up -d db backend web`. Caddy fica desligado. Nada muda para o publico.

**D. Validar por tunel SSH (`-L 8081:127.0.0.1:8081`):** `/actuator/health`; Flyway aplicou V1..V15 em Postgres 16 (o risco que os testes nao cobrem); login; primeira socia criada por `deploy/oci/criar-socia.sh` (senha digitada por voce, nunca no chat); upload de um anexo e geracao de um PDF (volume e memoria); `free -h` antes/depois.

**E. Expor com HTTPS (decisao sua):** subir o Caddy (perfil `https`, `DOMINIO=www...`) so se o DNS estiver como esperado e se voce quiser o sistema publico agora; senao manter fechado e, se o site estiver fora do ar, decidir entre subir so o site (nginx serve `/`) ou reverter o DNS.

**F. Operacao:** cron do `backup.sh` com passphrase em arquivo 600 e copia fora da VM; **teste de restauracao** (`deploy/restore.md`); depois, com e-mail e OABs configurados, ligar `APP_ALERTAS_AGENDADO` e `APP_CAPTURA_AGENDADA`.

## Rollback
Fases C e D sao aditivas: `docker compose -p crispim down` (sem `-v`), banco/volumes preservados; o Divas Nete nao muda. Migracoes nao tem "down": a volta de dados e pelo backup da Fase B.

## O que eu preciso de voce
1. OK para a Fase A (leitura) e para usar a chave `crispim_ed25519`.
2. Commitar os ajustes de deploy (os seus e os meus) juntos? 
3. Confirmar se o site publico esta fora do ar e qual caminho preferir na Fase E.
4. Quem digita a senha da primeira socia (na VM, por `criar-socia.sh`).


## Resultado da Fase A (2026-10-19, somente leitura, nada alterado)
- VM `crispim-micro`: x86_64, Ubuntu 24.04.5, Docker 29.9.1 / Compose 5.6.0, 954 MiB de RAM (678 MiB em uso, 275 MiB disponiveis), swap de 4 GiB (262 MiB usados), disco 48 GB com 38 GB livres. Nenhum outro servico (sem ngrok nesta VM).
- Containers do projeto `crispim` no ar: db (Postgres 16.15, saudavel), backend (195 MiB; subiu em 70 s), web (loopback 8081) e caddy (80/443). Firewall local libera 22, 80 e 443.
- **O site publico esta no ar com HTTPS**: Caddy obteve certificados Let's Encrypt para apex e `www`; `www/` 200, `www/app/` 200, apex 301 para `www`, `/api/me` 401 sem login, `/actuator/health` 200 (publico, so estado). DNS de `@` e `www` -> <IP_DA_VM>.
- **Banco em V1**: Flyway com so a versao 1; tabelas `usuario`, `oab`, `log_auditoria`; **1 usuario** (a primeira socia) e 5 registros de auditoria. O backend em producao e a versao antiga (so login).
- `.env` (modo 600) tem DB_PASSWORD, COOKIE_SECURE, WEB_PORT, JAVA_OPTS, DOMINIO e DOMINIO_APEX; nao tem as variaveis novas (todas tem padrao seguro).
- Consequencias para o deploy: (1) o sistema ja esta publico, entao o deploy atualiza algo exposto: a janela de troca do backend deixa `/api` fora por ~1-2 min (startup lento com 1 CPU e 15 migracoes); (2) ha dado real minimo (a conta da socia): backup antes e obrigatorio; (3) memoria apertada: construir backend e web em sequencia usando o swap, e acompanhar `free -h` e `docker stats` depois.

## Resultado da Fase B (2026-10-19, backup; unica escrita na VM foi criar os arquivos abaixo)
- `~/backups/crispim/gestao-pre-v15-20261019-195833.sql.gz.enc` (pg_dump do banco em V1, 2.240 bytes) e `~/backups/crispim/env-20261019-195833.enc` (copia do `.env`), ambos AES-256-CBC com PBKDF2, modo 600, pasta 700. Passphrase gerada na VM em `~/.crispim-backup.pass` (modo 600).
- Verificado sem imprimir dados: descriptografa e descompacta; 4 tabelas (`flyway_schema_history`, `log_auditoria`, `oab`, `usuario`); 1 linha em `usuario` e 5 em `log_auditoria`; dump termina corretamente.
- SHA-256: dump `2c02d3ff1f1c0a60...476b`, env `b98de6970b1b31d9...2128`.
- NAO feito: copia fora da VM e teste de restauracao. A passphrase esta na mesma VM dos arquivos; enquanto nao houver copia externa, o backup nao protege contra perda da VM.

## Fase C - passo a passo detalhado (PARA APROVACAO; nada foi executado)
Objetivo: trocar SO o backend pela versao completa (V2 a V15). `web`, `caddy` e `db` nao sao recriados; nao se cria usuario, nao se edita `.env`, nao se ligam rotinas diarias.

**C0. Local, antes de enviar (preparacao)**
1. Acrescentar `ENV MAVEN_OPTS="-Xmx350m"` ao estagio de build do `backend/Dockerfile` (a VM tem ~275 MiB livres; limita o Maven) e commitar.
2. Gerar o pacote: tar de `backend/ frontend/ deploy/ docs/ site/ README.md CLAUDE.md .github/` com `--exclude=.git --exclude=node_modules --exclude=target --exclude=dist --exclude=deploy/.env --exclude=site/testeLogin --exclude=site/project-rag --exclude=backend/storage` (~1 MB). Conferir a lista de arquivos e que nao ha `.env`, chave ou dado real.
3. Calcular SHA-256 do pacote.

**C1. VM, somente leitura (decide se o `web` precisa de rebuild)**
4. Comparar o SHA-256 dos arquivos de `frontend/`, `site/` (publico), `deploy/nginx.conf` e `deploy/web.Dockerfile` entre o pacote e `~/crispim-campos`. Se iguais, o `web` NAO e reconstruido (menos RAM, sem risco para o site). Se diferentes, volto a voce.
5. `free -h`, `docker stats --no-stream`, `df -h`: so prossigo com >= 200 MiB disponiveis (RAM+swap livre) e >= 10 GB de disco.

**C2. VM, escritas (aditivas)**
6. Enviar o pacote por `scp` para `~/` e conferir o SHA-256 la.
7. Marcar a imagem atual para rollback: `docker tag crispim-backend:latest crispim-backend:antes-v15` (so um rotulo).
8. Preservar o codigo antigo: `mv ~/crispim-campos/backend ~/crispim-campos/backend.antes-v15`. Necessario porque um arquivo antigo deixado para tras pode conflitar com o novo (ex.: `RelogioConfig` mudou de pacote e daria Clock duplicado).
9. Extrair o pacote sobre `~/crispim-campos` (sobrepoe; `deploy/.env` fica intacto por nao estar no pacote). Conferir que `.env` continua modo 600 e com as mesmas 6 variaveis.

**C3. VM, construir sem trocar nada em execucao**
10. `docker compose -p crispim build backend` (so o backend; o container em execucao continua servindo). Em paralelo, a cada ~30 s: `free -h` e `docker stats`. Estimativa: 8 a 15 min (swap). Criterio de ABORTO: `db` ou `backend` em execucao reiniciar/ser morto pelo OOM, ou RAM+swap livres < 100 MiB; nesse caso interrompo o build e reporto (nada foi trocado).

**C4. VM, troca (janela de ~1 a 2 min sem `/api`; o site estatico segue no ar)**
11. `docker compose -p crispim --profile https up -d --no-deps backend` (cria o volume `storage`, recria so o backend; Flyway aplica V2..V15 no inicio; inicializacao ~70 a 120 s).
12. Esperar `Started GestaoApplication` (limite 5 min). Verificar: `flyway_schema_history` com versoes 1..15 todas `success`; `/actuator/health` UP; `GET /api/me` 401; site `/` e `/app/` 200; `docker stats` e `free -h` depois.

**C5. Criterios de sucesso / rollback**
- Sucesso: health UP, Flyway 1..15 ok, 1 usuario preservado, site e HTTPS inalterados, RAM estavel.
- Falha antes de iniciar (Flyway aplica cada migracao em transacao): o banco fica na ultima versao boa. Rollback do codigo: `docker tag crispim-backend:antes-v15 crispim-backend:latest` + `up -d --no-deps backend`. O codigo antigo continua compativel com o esquema novo (so houve acrescimos; Hibernate valida apenas o que mapeia).
- Falha grave de dados: restaurar o backup da Fase B (`deploy/restore.md`). OBSERVACAO: esse backup ainda nao teve a restauracao testada; o risco e baixo (banco com 1 usuario, esquema so aditivo), mas e o motivo de manter o teste como recomendado.

**Fora desta fase:** criar usuarios, ativar 2FA da socia, rotinas diarias, SMTP, copia/rotina de backup (Fases D a F). Na D, recomendo ativar o 2FA da conta da socia, pois a API completa passa a ficar publica atras apenas de senha.

## Resultado da Fase C (2026-10-19, executada com aprovacao)
- C0: pacote de 0,96 MB (358 arquivos, SHA-256 `b3f587b0...d90e`), sem `.env`, chave, `testeLogin` nem `project-rag`. Commit `70c8846` (`MAVEN_OPTS=-Xmx350m`).
- C1: os 33 arquivos de web/site/proxy eram identicos aos da VM: o `web` NAO foi reconstruido. Antes: 258 MiB de RAM disponivel, 3,7 GiB de swap livre, 38 GB de disco.
- C2: pacote enviado e conferido por hash; imagem antiga rotulada `crispim-backend:antes-v15`; codigo antigo em `~/crispim-campos/backend.antes-v15`; `.env` intacto (600, 6 variaveis).
- C3: build do backend em ~6 min (exit 0), sem OOM nem reinicio de containers; imagem nova `6b512c783c21` (593 MB).
- C4: troca as 20:15 UTC; backend subiu em ~2m30s; Flyway versoes 1 a 15 todas `success` em Postgres 16.15; 28 tabelas e os 4 triggers de imutabilidade; 1 usuario preservado; 0 reinicios, sem erros no log; `/data/storage` gravavel pelo usuario `gestao`; RAM apos a troca: 293 MiB disponiveis, backend 240 MiB.
- Visto de fora (HTTPS): `/` 200, `/app/` 200, apex 301, `/api/me` e `/api/usuarios` 401, `/dev/index.html` 404 (console de teste nao foi publicado), cabecalhos CSP, Referrer-Policy, HSTS, nosniff e X-Frame-Options presentes.
- Nao alterados: `web`, `caddy`, `db`, `.env`, DNS. Rotinas diarias seguem desligadas. Rollback disponivel (rotulo `antes-v15`), nao usado.
- Pendente (Fases D a F): validar login real e 2FA da socia, upload e PDF na VM, criar usuarios e dados reais, SMTP, rotina e restauracao de backup. Observacao: a API completa esta publica atras so de senha; ativar o 2FA da conta da socia.

## Fases F-prep e D (2026-10-19, executadas com aprovacao; plano D a F aprovado na ordem F-prep, D1, D2, E, F)
- **Etapa 0 (leitura)**: 4 containers no ar, 0 reinicios, 311 MiB de RAM disponivel e 3,7 GiB de swap livre, 38 GB de disco; o usuario `ubuntu` esta no grupo `docker`; sem cron de root.
- **F-prep**: `deploy/backup.sh` corrigido (projeto Compose `crispim` fixo; grava em `.tmp`, confere que decifra e descompacta, tamanho minimo; retencao 30 dias preservando os 7 mais recentes de cada tipo). Backup manual executado na VM (`gestao-20261019-202658` e `storage-...`, 9.888 e 144 bytes) e copiado para `~/backups-crispim` por `deploy/oci/puxar-backups.sh` (rsync pela chave `crispim_ed25519`). Novos scripts locais: `guardar-passphrase.sh` (baixa a passphrase para arquivo 600 sem imprimi-la; a usuaria guarda copia no gerenciador de senhas).
- **D1 (preparado, NAO executado)**: `deploy/oci/ativar-2fa.sh` (login com senha oculta, mostra so a URI otpauth, confirma o codigo e testa novo login) e `desativar-2fa.sh` (emergencia, SQL). O login troca o token CSRF: apos `POST /api/login` e preciso `GET /api/csrf` de novo antes de qualquer POST (sem isso, 403); o frontend precisa fazer o mesmo.
- **D2 (stack temporaria `crispimtmp`, dados ficticios, removida no fim; producao intacta, health 200, 0 reinicios, RAM+swap livres ~3,5 GiB)**: Flyway/Postgres 16 ok; login 204; cliente e processo criados; **upload 201** (arquivo gravado em `/data/storage` com nome UUID); **PDF interno (AGENDA_DIA) 200, ~68 KB, %PDF**; PDF do cliente exige aprovacao (422, esperado; nao testado aprovado). Script: `deploy/oci/teste-d2.sh` com `deploy/docker-compose.tmp.yml`.
- **DOIS BUGS DE BACKEND ENCONTRADOS (nao corrigidos; backend nao e escopo desta sessao)**: (1) `AnexoService.baixar` e `@Transactional(readOnly = true)` mas grava auditoria `ANEXO_BAIXADO`: no Postgres o download de anexo da 500 ("cannot execute INSERT in a read-only transaction"); (2) `BootstrapSocia` nao publica `UsuarioCriado`, entao a primeira socia (criada por bootstrap, como a de producao, id 1) nasce SEM regras de visibilidade (`regra_visibilidade`) e nao ve processos, agendamentos nem publicacoes (404). No D2 contornei por SQL so na stack temporaria. Correcao proposta: tirar `readOnly` de `baixar`; `BootstrapSocia` publicar o evento; e migracao V16 idempotente que insere as regras proprias (observador = titular, tres escopos) para socias/advogados ativos sem elas, o que repara a conta de producao.

## Correcao dos dois bugs e troca do backend (2026-10-19, com aprovacao)
- Commit `31277c6`: `AnexoService.baixar` deixa de ser `readOnly`; `BootstrapSocia` publica `UsuarioCriado`; migracao `V16__regras_proprias_da_socia.sql` (idempotente; so para SOCIA/ADVOGADO ativos sem nenhuma regra). `PostgresRealTest` agora espera 16 migracoes. NAO executei os testes (sem JDK nesta maquina); a validacao foi o deploy.
- VM: backup `20261019-205321` antes; pacote so dos 4 arquivos (SHA-256 `e026fe32...`); imagem anterior rotulada `crispim-backend:antes-v16` (rollback); build ~4 min sem OOM; troca com `up -d --no-deps backend`; subiu em 92 s; Flyway 15 e 16 `success`; a socia (id 1) agora tem as 3 regras proprias; 1 usuario; health 200, `/api/me` 401; 0 reinicios, RAM+swap livres ~4 GiB.
- Observacao: durante a janela o container `web` foi recriado pela outra sessao (frontend); nao foi reinicio por falha (0 restarts, sem OOM).
- Pendente: ver o download de anexo e a visibilidade em producao so quando houver dados reais; D1 (2FA) e Fases E e F seguem.
