#!/usr/bin/env bash
# Baixa a passphrase dos backups da VM para um arquivo local (modo 600) SEM imprimi-la.
# Guarde tambem uma copia no seu gerenciador de senhas: sem ela os backups nao abrem.
set -euo pipefail
CHAVE="${CHAVE:-$HOME/.ssh/crispim_ed25519}"
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
VM="${VM:?defina VM=usuario@host (ou deploy/oci/local.env)}"
DESTINO="${DESTINO:-$HOME/.crispim-backup.pass}"
[ ! -e "$DESTINO" ] || { echo "$DESTINO ja existe; nada feito."; exit 1; }
umask 077
ssh -i "$CHAVE" -o BatchMode=yes -o IdentitiesOnly=yes "$VM" 'cat ~/.crispim-backup.pass' > "$DESTINO"
chmod 600 "$DESTINO"
echo "Passphrase salva em $DESTINO ($(wc -c < "$DESTINO") bytes). Copie-a para o gerenciador de senhas: cat $DESTINO"
