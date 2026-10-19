# Plano de deploy na VM Oracle (sem acesso a VM ate a autorizacao)

Base: `docs/08-infra-oracle.md`. Nada abaixo foi executado. Cada fase so avanca com OK da usuaria; a VM roda o Divas Nete em producao.

## Lacunas no repositorio antes de subir (resolvidas localmente em 2026-10-19, nao validadas com Docker)
1. Layout unificado: nginx (`deploy/nginx.conf`) serve o site em `/` e o app Vue em `/app/` (`base: '/app/'` no Vite), `/api` vai ao backend. Imagem web em `deploy/web.Dockerfile` (contexto na raiz; `testeLogin/` e RAG do site ficam fora da imagem).
2. TLS: nginx interno escuta so `80` e repassa `X-Forwarded-Proto` do proxy da VM; Spring usa `forward-headers-strategy: framework`; manter `COOKIE_SECURE=true`.
3. Compose publica `127.0.0.1:${WEB_PORT:-8081}:80` (so loopback; exposicao via proxy).
4. VM: a VM do Divas Nete (x86, 954 MB RAM) nao comporta o sistema; usar uma segunda VM (Ampere A1 ARM, Always Free). Imagens base sao multi-arch.
5. Compose/CI/Flyway em Postgres real ainda NAO validados: validar na VM nova.

## Fase 0 - Leitura da VM (precisa chave SSH + OK)
Somente leitura: `uname -m`, `free -h`, `df -h`, `docker --version`, `docker compose version`, `docker ps`, `ss -ltnp` (portas livres), `systemctl list-units | grep -i ngrok`, regras de firewall local. Registrar em `docs/08` e nao tocar nos containers do Divas Nete.

## Fase 1 - Preparar o espaco (aditivo)
- Pasta `/home/ubuntu/crispim-campos`, `.env` proprio (modo 600) com `DB_PASSWORD` forte, `COOKIE_SECURE=true`, `WEB_PORT=8081`, bootstrap da primeira socia (senha 12+ chars digitada pela usuaria, nunca no chat).
- Projeto Compose isolado: `docker compose -p crispim ...`; volumes e rede proprios.
- Se faltar RAM (build Maven + Node), criar swap ou buildar a imagem fora da VM e enviar (`docker save | ssh ... docker load`).

## Fase 2 - Subir sem expor publicamente
- Enviar o codigo por tar via SSH excluindo `.env`, `node_modules`, `target`, `dist` (padrao do Divas Nete) e rodar `docker compose -p crispim up -d --build`.
- Validar por tunel SSH (`ssh -L 8081:127.0.0.1:8081`): `/actuator/health`, login, Flyway aplicado, bootstrap criou a primeira socia, auditoria gravando.
- Antes e depois: `docker stats`/`free -h` para confirmar que o Divas Nete nao foi afetado e que `curl 127.0.0.1:8080/health` continua `ok`.

## Fase 3 - Expor com HTTPS (decisao pendente)
- Opcao A (recomendada): Caddy na VM em 80/443 com Let's Encrypt para `app.<dominio>` (e depois o site), proxy para `127.0.0.1:8081`; exige liberar 80/443 na Security List/NSG da OCI e DNS apontando para o IP. O ngrok do Divas Nete segue intocado.
- Opcao B: Cloudflare Tunnel (precisa dominio na Cloudflare) ou ngrok pago.
- DNS do dominio atual (GitHub Pages) NAO muda sem autorizacao; comecar por subdominio de teste.

## Fase 4 - Operacao
- Backup: `deploy/backup.sh` por cron na VM (`BACKUP_DIR` proprio, ex.: `/home/ubuntu/backups/crispim`, passphrase em arquivo 600); copia fora da VM; testar `restore.md` antes de usar dados reais.
- Deploys seguintes: backup datado, reconstruir so o servico alterado, validar health interno/HTTPS; nunca `down -v`.
- Dado real de cliente so entra depois de backup restaurado com sucesso, HTTPS ativo e revisao de LGPD/invariantes.

## Rollback
Fases 1-3 sao aditivas: reverter = `docker compose -p crispim down` (sem `-v` ate decidir descartar dados), remover o bloco do Caddy e reabrir/fechar portas OCI. O Divas Nete nunca e alterado.

## Decisoes que dependem da usuaria
Chave SSH/acesso (e quem executa); subdominio e controle do DNS; opcao A ou B; site em `/` com app em `/app` ou subdominios separados.

Atualizacao 2026-10-19: plano revisado para o sistema completo (migracoes V2 a V15, volume de anexos, backup do storage, build leve) em `docs/12-plano-deploy-sistema-completo.md`.
