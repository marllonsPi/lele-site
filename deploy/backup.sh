#!/usr/bin/env bash
# Backup criptografado do Postgres e do volume `storage` (anexos e modelos). Agende via cron na VM.
# Requer: BACKUP_DIR e BACKUP_PASSPHRASE_FILE (arquivo 600 com a senha). Projeto Compose: crispim.
# Cada arquivo e gravado como .tmp, conferido (descriptografa e descompacta) e so entao renomeado.
# Retencao: 30 dias, mas sempre preserva os 7 mais recentes de cada tipo. Restauracao: deploy/restore.md
set -euo pipefail
cd "$(dirname "$0")"
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-crispim}"
: "${BACKUP_DIR:?defina BACKUP_DIR}"
: "${BACKUP_PASSPHRASE_FILE:?defina BACKUP_PASSPHRASE_FILE}"
[ -s "$BACKUP_PASSPHRASE_FILE" ] || { echo "Passphrase ausente ou vazia" >&2; exit 1; }
umask 077
mkdir -p "$BACKUP_DIR"
carimbo="$(date +%Y%m%d-%H%M%S)"
openssl_enc() { openssl enc -aes-256-cbc -pbkdf2 -salt -pass "file:$BACKUP_PASSPHRASE_FILE" "$@"; }

# $1 = arquivo final, $2 = tamanho minimo em bytes, demais = comando que escreve o conteudo em stdout
gerar() {
  local final="$1" minimo="$2"; shift 2
  local tmp="$final.tmp"
  trap 'rm -f "$tmp"' RETURN
  "$@" | gzip | openssl_enc -out "$tmp"
  [ "$(stat -c %s "$tmp")" -ge "$minimo" ] || { echo "Backup menor que o esperado: $final" >&2; return 1; }
  openssl_enc -d -in "$tmp" | gunzip -t   # prova que descriptografa e descompacta
  mv "$tmp" "$final"
}

gerar "$BACKUP_DIR/gestao-$carimbo.sql.gz.enc" 1000 \
  docker compose exec -T db pg_dump -U gestao -d gestao --no-owner
gerar "$BACKUP_DIR/storage-$carimbo.tar.gz.enc" 40 \
  docker compose exec -T backend tar -C /data -c storage

# retencao: apaga o que passou de 30 dias, preservando os 7 mais recentes de cada tipo
for padrao in 'gestao-*.sql.gz.enc' 'storage-*.tar.gz.enc'; do
  ls -1t "$BACKUP_DIR"/$padrao 2>/dev/null | tail -n +8 | while read -r f; do
    [ -n "$(find "$f" -mtime +30 -print)" ] && rm -f -- "$f"
  done
done
echo "Backup ok: $carimbo"
