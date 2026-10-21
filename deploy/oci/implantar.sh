#!/usr/bin/env bash
# Implanta o codigo local na VM crispim-micro (1 GB de RAM), seguindo a disciplina de deploy da RAG:
# medir -> backup (banco + anexos + codigo + imagem) -> enviar -> construir EM SEQUENCIA -> subir so backend/web -> validar.
# Roda NESTA maquina, de dentro de deploy/oci/. Nunca imprime segredos; nao toca no .env da VM nem nos volumes.
# Uso:  bash implantar.sh            (faz tudo)
#       bash implantar.sh --so-ler   (apenas mede e mostra o estado da VM)
# Rollback: ver o final desta saida (imagem crispim-backend:antes-<carimbo> e pasta backend.antes-<carimbo>).
set -euo pipefail
export PATH="$HOME/.local/node/bin:$PATH"   # Node fora do PATH padrao nesta maquina

CHAVE="${CHAVE:-$HOME/.ssh/crispim_ed25519}"
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
VM="${VM:?defina VM=usuario@host (ou deploy/oci/local.env)}"
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
CARIMBO="$(date +%Y%m%d-%H%M)"
SSH=(ssh -i "$CHAVE" -o BatchMode=yes -o IdentitiesOnly=yes)
remoto() { "${SSH[@]}" "$VM" "$@"; }
DC='cd ~/crispim-campos/deploy && docker compose -p crispim'

echo "== 1. Estado da VM (somente leitura) =="
remoto "free -h | sed -n 1,2p; df -h / | tail -1; $DC ps --format '{{.Name}}  {{.Status}}'; ls -ld ~/crispim-campos/deploy/.env"
remoto "$DC exec -T db psql -U gestao -d gestao -tAc \"select 'Flyway no head: V'||max(version::int) from flyway_schema_history where success\""
[ "${1:-}" = "--so-ler" ] && exit 0

echo "== 2. Conferencias locais =="
(cd "$RAIZ/frontend" && npx --no-install vue-tsc --noEmit -p tsconfig.app.json) || { echo "Tipos do frontend falharam: abortando." >&2; exit 1; }

echo "== 3. Backup antes de mexer (banco, anexos, codigo, imagem) =="
# backup.sh corrigido primeiro (a retencao saia com erro quando havia mais de 7 copias, apos gerar o backup)
remoto "cat > ~/crispim-campos/deploy/backup.sh.novo && chmod +x ~/crispim-campos/deploy/backup.sh.novo && mv ~/crispim-campos/deploy/backup.sh.novo ~/crispim-campos/deploy/backup.sh" < "$RAIZ/deploy/backup.sh"
remoto "BACKUP_DIR=\$HOME/backups/crispim BACKUP_PASSPHRASE_FILE=\$HOME/.crispim-backup.pass ~/crispim-campos/deploy/backup.sh"
remoto "cd ~ && cp -a crispim-campos/backend backend.antes-$CARIMBO.tmp && mv backend.antes-$CARIMBO.tmp crispim-campos/backend.antes-$CARIMBO \
  && (docker tag crispim-backend:latest crispim-backend:antes-$CARIMBO 2>/dev/null || docker tag \$(docker compose -p crispim -f crispim-campos/deploy/docker-compose.yml images -q backend | head -1) crispim-backend:antes-$CARIMBO)"
bash "$(dirname "$0")/puxar-backups.sh"   # copia o backup recem-feito para esta maquina

echo "== 4. Enviar o codigo (sem .env, node_modules, target, dist, dados reais) =="
tar -C "$RAIZ" --exclude='.env' --exclude='node_modules' --exclude='target' --exclude='dist' --exclude='.git' \
  --exclude='*.xlsx' --exclude='ai' --exclude='deploy/oci/*.pass' -czf - backend frontend site deploy docs \
  | remoto "tar -C ~/crispim-campos -xzf -"

echo "== 5. Construir em sequencia (1 GB de RAM: nunca em paralelo) =="
remoto "$DC build backend"
remoto "$DC build web"

echo "== 6. Subir so o que mudou (sem recriar banco/Caddy, sem down, sem remover volumes) =="
remoto "$DC up -d --no-deps backend"
for i in $(seq 1 30); do
  if remoto "docker exec \$($DC ps -q backend) wget -qO- http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '\"UP\"'"; then ok=1; break; fi
  sleep 5
done
[ "${ok:-0}" = 1 ] || { echo "Backend nao ficou saudavel. Veja: $DC logs --tail=80 backend  | Rollback abaixo." >&2; exit 1; }
remoto "$DC up -d --no-deps web"

echo "== 7. Validar =="
remoto "$DC exec -T db psql -U gestao -d gestao -tAc \"select 'Flyway no head: V'||max(version::int) from flyway_schema_history where success\""
remoto "$DC logs --tail=40 backend | grep -i -E 'error|exception' || echo 'logs do backend sem erro'"
code="$(curl -s -o /dev/null -w '%{http_code}' https://www.crispimcamposadvogados.com.br/app/ || true)"
echo "HTTPS publico /app/: HTTP $code"
remoto "free -h | sed -n 1,2p"

cat <<EOF

Implantado ($CARIMBO). Rollback, se precisar (na VM):
  docker tag crispim-backend:antes-$CARIMBO crispim-backend:latest && cd ~/crispim-campos/deploy && docker compose -p crispim up -d --no-deps backend
  codigo anterior do backend: ~/crispim-campos/backend.antes-$CARIMBO   |   backups: ~/backups/crispim (e ~/backups-crispim aqui)
  Obs.: migracoes Flyway ja aplicadas nao voltam sozinhas; restaurar o banco so pelo deploy/restore.md.
EOF
