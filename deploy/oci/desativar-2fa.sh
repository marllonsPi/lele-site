#!/usr/bin/env bash
# EMERGENCIA: desativa o 2FA de um usuario direto no banco (perdeu o autenticador). Pede confirmacao.
# Depois de usar, reative com ativar-2fa.sh. A auditoria nao registra este SQL: anote o motivo em docs/12.
set -euo pipefail
CHAVE="${CHAVE:-$HOME/.ssh/crispim_ed25519}"
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
VM="${VM:?defina VM=usuario@host (ou deploy/oci/local.env)}"
read -r -p "E-mail do usuario para desativar o 2FA: " EMAIL
case "$EMAIL" in *"'"*|"") echo "E-mail invalido."; exit 1;; esac
read -r -p "Confirma desativar o 2FA de $EMAIL? (digite SIM) " OK
[ "$OK" = "SIM" ] || { echo "Cancelado."; exit 1; }
printf '%s\n' "$EMAIL" | ssh -i "$CHAVE" -o BatchMode=yes -o IdentitiesOnly=yes "$VM" '
read -r E
docker exec -i crispim-db-1 sh -c "psql -v ON_ERROR_STOP=1 -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\"" <<SQL
UPDATE usuario SET totp_ativo = false, totp_secret = null WHERE email = '"'"'$E'"'"';
SQL
'
