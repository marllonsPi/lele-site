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
