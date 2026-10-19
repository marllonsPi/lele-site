#!/usr/bin/env bash
# Redefine a senha de um usuario existente na VM. Roda na maquina da usuaria: pede o e-mail e a
# nova senha (oculta), gera o hash BCrypt (custo 12) localmente e envia so o hash por stdin via
# SSH; a senha em texto nunca sai desta maquina. Senha: 12+ caracteres.
set -euo pipefail

CHAVE="${CHAVE:-$HOME/.ssh/crispim_ed25519}"
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
VM="${VM:?defina VM=usuario@host (ou deploy/oci/local.env)}"

read -r -p "E-mail do usuario: " EMAIL
read -r -s -p "Nova senha (12+ caracteres): " SENHA; echo
read -r -s -p "Repita a nova senha: " SENHA2; echo

[ "$SENHA" = "$SENHA2" ] || { echo "As senhas nao conferem."; exit 1; }
[ "${#SENHA}" -ge 12 ] || { echo "A senha precisa ter 12 ou mais caracteres."; exit 1; }
case "$EMAIL" in *"'"*|"") echo "E-mail invalido."; exit 1;; esac

HASH=$(SENHA="$SENHA" python3 -I -c 'import bcrypt,os;print(bcrypt.hashpw(os.environ["SENHA"].encode(),bcrypt.gensalt(12,prefix=b"2a")).decode())')
unset SENHA SENHA2

printf '%s\n%s\n' "$EMAIL" "$HASH" | ssh -i "$CHAVE" -o BatchMode=yes "$VM" '
read -r E; read -r H
sudo docker exec -i crispim-db-1 sh -c "psql -v ON_ERROR_STOP=1 -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\"" <<SQL
UPDATE usuario SET senha_hash = '"'"'$H'"'"' WHERE email = '"'"'$E'"'"';
SQL
'
echo "Se acima aparece 'UPDATE 1', a senha foi trocada. Se 'UPDATE 0', o e-mail nao existe."
