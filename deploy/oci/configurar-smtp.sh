#!/usr/bin/env bash
# Configura o SMTP do Gmail na VM. Pede o e-mail Gmail e a SENHA DE APP (16 letras, oculta); envia por stdin via SSH,
# grava no .env da VM (600) e recria so o backend com o override docker-compose.smtp.yml.
# Senha de app: conta Google > Seguranca > Verificacao em duas etapas ligada > Senhas de app. NAO use a senha normal.
# Nao liga APP_ALERTAS_AGENDADO nem APP_CAPTURA_AGENDADA.
set -euo pipefail
CHAVE="${CHAVE:-$HOME/.ssh/crispim_ed25519}"
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
VM="${VM:?defina VM=usuario@host (ou deploy/oci/local.env)}"
read -r -p "Conta Gmail que envia (tambem sera o remetente): " USUARIO
read -r -s -p "Senha de app do Google: " SENHA; echo
SENHA="${SENHA// /}"
case "$USUARIO$SENHA" in *"'"*|*" "*|"") echo "Valores invalidos."; exit 1;; esac
[ "${#SENHA}" -eq 16 ] || { echo "A senha de app tem 16 letras."; exit 1; }
printf '%s\n%s\n' "$USUARIO" "$SENHA" | ssh -i "$CHAVE" -o BatchMode=yes -o IdentitiesOnly=yes "$VM" '
set -e
cd ~/crispim-campos/deploy
read -r U; read -r S
cp .env ".env.antes-smtp"; chmod 600 .env.antes-smtp
grep -v "^SMTP_" .env > .env.tmp; chmod 600 .env.tmp
printf "SMTP_HOST=smtp.gmail.com\nSMTP_PORT=587\nSMTP_USUARIO=%s\nSMTP_SENHA=%s\nSMTP_REMETENTE=%s\n" "$U" "$S" "$U" >> .env.tmp
mv .env.tmp .env
docker compose -p crispim --profile https -f docker-compose.yml -f docker-compose.smtp.yml up -d --no-deps backend 2>&1 | tail -2
for i in $(seq 1 60); do docker logs crispim-backend-1 2>&1 | grep -q "Started .*Application" && { echo "Backend no ar com SMTP."; exit 0; }; sleep 5; done
echo "Tempo esgotado; rollback: cp .env.antes-smtp .env e recriar sem o override."; exit 1'
unset SENHA
echo "Pronto. Teste com deploy/oci/testar-email.sh."
