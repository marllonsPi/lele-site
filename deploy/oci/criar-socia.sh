#!/usr/bin/env bash
# Cria a primeira socia na VM. Roda na maquina da usuaria: pede nome, e-mail e senha
# (digitada oculta), envia por stdin via SSH (nunca em argumento nem no historico),
# reinicia o backend para o BootstrapSocia criar o usuario e apaga a senha do .env da VM.
# So funciona com a tabela de usuarios vazia. Senha: 12+ caracteres, sem aspas simples.
set -euo pipefail

CHAVE="${CHAVE:-$HOME/.ssh/crispim_ed25519}"
[ -f "$(dirname "$0")/local.env" ] && . "$(dirname "$0")/local.env"
VM="${VM:?defina VM=usuario@host (ou deploy/oci/local.env)}"
PASTA="${PASTA:-crispim-campos/deploy}"

read -r -p "Nome completo da socia: " NOME
read -r -p "E-mail (sera o login): " EMAIL
read -r -s -p "Senha (12+ caracteres): " SENHA; echo
read -r -s -p "Repita a senha: " SENHA2; echo

[ "$SENHA" = "$SENHA2" ] || { echo "As senhas nao conferem."; exit 1; }
[ "${#SENHA}" -ge 12 ] || { echo "A senha precisa ter 12 ou mais caracteres."; exit 1; }
case "$SENHA$NOME$EMAIL" in *"'"*) echo "Nao use aspas simples (') nos campos."; exit 1;; esac
[ -n "$NOME" ] && [ -n "$EMAIL" ] || { echo "Nome e e-mail sao obrigatorios."; exit 1; }

printf '%s\n%s\n%s\n' "$NOME" "$EMAIL" "$SENHA" | ssh -i "$CHAVE" -o BatchMode=yes "$VM" "
set -e
cd ~/$PASTA
read -r N; read -r E; read -r S
limpar() { grep -v '^BOOTSTRAP_SOCIA_' .env > .env.tmp || true; chmod 600 .env.tmp; mv .env.tmp .env; }
trap 'limpar; sudo docker compose -p crispim up -d --no-deps backend >/dev/null 2>&1 || true' EXIT
limpar
printf \"BOOTSTRAP_SOCIA_NOME='%s'\nBOOTSTRAP_SOCIA_EMAIL='%s'\nBOOTSTRAP_SOCIA_SENHA='%s'\n\" \"\$N\" \"\$E\" \"\$S\" >> .env
sudo docker compose -p crispim up -d --no-deps backend
for i in \$(seq 1 40); do
  if sudo docker logs crispim-backend-1 2>&1 | grep -q 'Primeira socia criada'; then echo 'OK: primeira socia criada.'; exit 0; fi
  if sudo docker logs crispim-backend-1 2>&1 | grep -q 'Started .*Application'; then
    sleep 5
    if sudo docker logs crispim-backend-1 2>&1 | grep -q 'Primeira socia criada'; then echo 'OK: primeira socia criada.'; exit 0; fi
    echo 'Backend subiu, mas nenhuma socia foi criada (a base ja tem usuarios?).'; exit 2
  fi
  sleep 3
done
echo 'Tempo esgotado esperando o backend.'; exit 3
"
unset SENHA SENHA2
echo "Pronto. A senha foi removida do .env da VM. Entre em https://www.crispimcamposadvogados.com.br/app/"
