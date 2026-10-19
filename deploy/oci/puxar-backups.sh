#!/usr/bin/env bash
# Copia os backups (ja criptografados) da VM para esta maquina. A VM nao guarda credencial externa.
# Agendar localmente (ex.: systemd --user timer diario). Nao apaga nada local.
set -euo pipefail
CHAVE="${CHAVE:-$HOME/.ssh/crispim_ed25519}"
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
VM="${VM:?defina VM=usuario@host (ou deploy/oci/local.env)}"
DESTINO="${DESTINO:-$HOME/backups-crispim}"
umask 077
mkdir -p "$DESTINO"
rsync -a --ignore-existing -e "ssh -i $CHAVE -o BatchMode=yes -o IdentitiesOnly=yes" "$VM:backups/crispim/" "$DESTINO/"
echo "Backups sincronizados em $DESTINO"
