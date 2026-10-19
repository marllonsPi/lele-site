# Infra: VM Oracle (OCI) compartilhada com o Divas Nete

Fontes (lidas em 2026-10-19): RAG central `marllonsPi/ai` e RAG local `marllonsPi/DivasNete` (`project-rag/memory/handoff.md`, `compose.oracle.yaml`). A VM hoje serve o Divas Nete em producao com clientes reais: qualquer mudanca nossa nao pode afeta-lo.

## Acesso (fato)
- Host `<IP_DA_VM_ORACLE>`, usuario `ubuntu`, SSH por chave `oci_bot_ed25519` (modo 600) com `ssh -i <chave> ubuntu@<IP_DA_VM_ORACLE>`. A chave esta so no outro ambiente da usuaria (`/home/marllons/.ssh/`); NAO existe nesta maquina. Nunca copiar a chave para Git/RAG/chat.
- Existiu um Bastion OCI temporario `divas-recovery-20260917` (STOPPED); a decisao de remove-lo e do Divas Nete.

## Como o Divas Nete roda (fato)
- Diretorio `/home/ubuntu/DivasNete`, sem `.git` (nao usar `git pull` na VM). Deploy = copiar arquivos (tar via SSH, excluindo `.env` e caches) e reconstruir so a API.
- Compose `compose.oracle.yaml`: Postgres 17, Redis 7 (appendonly), API FastAPI; API publicada em `8080:8000`; volumes `postgres-data`, `redis-data`.
- Exposicao HTTPS: tunel **ngrok** (conta gratis, dominio reservado) no servico systemd `ngrok-divas.service`, apontando para a porta **8080 do host**. Config do ngrok em `/home/ubuntu/.config/ngrok/ngrok.yml` (segredo; nunca ler para a RAG/logs).
- Backups antes de cada deploy em `/home/ubuntu/backups/` com sufixo `AAAAMMDD-HHMMSS` (PostgreSQL, Redis e codigo).
- Rotina de validacao pos-deploy: containers, Alembic no head, health `http://127.0.0.1:8080/health` + HTTPS publico, logs e hash dos arquivos host x container. Teste remoto em checkout limpo (sem `.env`).
- Proibido sem autorizacao: `docker compose down -v`, recriar Postgres/Redis, mexer em volumes.

## Implicacoes para subir o Crispim & Campos
1. **Porta 8080 e do Divas Nete** (e do ngrok). Nosso `web` precisa de outra porta (ex.: `WEB_PORT=8081`); nunca reutilizar 8080.
2. **ngrok gratis = um unico dominio/tunel ja ocupado.** Para expor o nosso sistema e o site com HTTPS e dominio proprio e preciso outro caminho: (a) proxy reverso na VM (Caddy/nginx com Let's Encrypt) nas portas 80/443, exigindo liberar ingress 80/443 na Security List/NSG da OCI e DNS do dominio; ou (b) segundo tunel/dominio (Cloudflare Tunnel, ou plano ngrok pago). Decisao pendente; (a) e a mais robusta, mas o Divas Nete continua no ngrok sem alteracao.
3. Stack isolada: pasta propria (ex.: `/home/ubuntu/crispim-campos`), projeto Compose com nome proprio (`docker compose -p crispim`), rede/volumes proprios, `.env` proprio com modo 600. Nao compartilhar Postgres/Redis do Divas Nete.
4. Backup do nosso Postgres (`deploy/backup.sh`) em diretorio proprio, mesmo padrao de sufixo datado, antes de cada deploy.
5. Mesma disciplina: deploy serial, medir RAM/disco antes (`free -h`, `df -h`, `docker stats`), reconstruir so o servico alterado, validar health interno e HTTPS.

## Pontos a confirmar (bloqueiam o deploy)
1. Shape (ARM/x86), RAM, disco livre, Docker/Compose instalados e versao; o `Dockerfile` do backend precisa ser compativel com a arquitetura.
2. Portas 80/443 livres e abertas na OCI? Algo ja escuta nelas?
3. Dominio: `crispimcamposadvogados.com.br` esta no GitHub Pages; mudar DNS exige autorizacao explicita da usuaria. Definir subdominio de teste (ex.: `app.`) antes de migrar o apex/www.
4. Estrategia de HTTPS (item 2 acima) e quem controla o DNS.
5. A usuaria precisa disponibilizar a chave SSH nesta maquina (ou executar os comandos ela mesma) e autorizar acesso a VM; nada sera executado na VM sem esse OK.
6. Entrega do codigo na VM: tar via SSH (padrao do Divas Nete) ou clone Git do repo do sistema (precisa credencial na VM).

## Atualizacao 2026-10-19 (decisao e execucao)
- A VM do Divas Nete (`Bot`) tem 1 GB de RAM: nao comporta o sistema. Decisao: VM propria do Crispim & Campos.
- Criada `crispim-micro` (E2.1.Micro, 1 GB, IP <IP_DA_VM>, chave `crispim_ed25519`), no lugar da `AppCidadao` (encerrada a pedido da usuaria; volume de boot mantido). A1 (12 GB) segue em retry no Cloud Shell.
- Rede: mesma VCN/subnet do Divas Nete; abrir apenas 80/443 de entrada na Security List (nao mexer em 8080/ngrok).
- Console OCI no painel do app: o assistente de criacao e instavel em painel estreito; o Cloud Shell nao recebe texto digitado pela automacao (colar pelo usuario).
