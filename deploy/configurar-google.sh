#!/usr/bin/env bash
# Grava a conexao Google (Gmail/Drive) no deploy/.env desta maquina, sem nada passar pelo chat.
# Uso, na VM, dentro de deploy/:  bash configurar-google.sh
# Pergunta o ID, a chave secreta (digitacao oculta) e os IDs das pastas do Drive; gera GOOGLE_TOKEN_KEY sozinho.
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { cp .env.example .env; echo "Criado .env a partir de .env.example (preencha as demais variaveis)."; }
chmod 600 .env

definir() { # chave valor: troca a linha existente ou acrescenta
  local k="$1" v="$2"
  if grep -q "^${k}=" .env; then
    sed -i "s|^${k}=.*|${k}=${v//|/\\|}|" .env
  else
    printf '%s=%s\n' "$k" "$v" >> .env
  fi
}

read -r -p "GOOGLE_CLIENT_ID (termina em .apps.googleusercontent.com): " id
read -r -s -p "GOOGLE_CLIENT_SECRET (nao aparece ao digitar): " segredo; echo
read -r -p "ID da pasta 'Documentos de Clientes' no Drive (final da URL; Enter pula): " pasta
read -r -p "ID da planilha financeira no Drive (Enter pula): " planilha

[ -n "$id" ] && [ -n "$segredo" ] || { echo "ID e chave sao obrigatorios." >&2; exit 1; }

definir GOOGLE_CLIENT_ID "$id"
definir GOOGLE_CLIENT_SECRET "$segredo"
definir GOOGLE_REDIRECT_URI "https://www.crispimcamposadvogados.com.br/api/google/callback"
if ! grep -q "^GOOGLE_TOKEN_KEY=.\+" .env; then
  definir GOOGLE_TOKEN_KEY "$(openssl rand -base64 32)"
  echo "GOOGLE_TOKEN_KEY gerada. Guarde uma copia do .env no cofre: sem ela, todos precisam reconectar."
fi
[ -n "$pasta" ] && definir IMPORTACAO_PASTA_CLIENTES_ID "$pasta"
[ -n "$planilha" ] && definir IMPORTACAO_PLANILHA_ID "$planilha"

echo "Pronto: .env atualizado (permissao 600). Reinicie o backend para valer: docker compose up -d backend"
