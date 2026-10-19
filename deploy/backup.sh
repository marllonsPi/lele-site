#!/usr/bin/env bash
# Backup diario criptografado do Postgres. Agende via cron/systemd timer no servidor.
# Requer: BACKUP_PASSPHRASE_FILE (arquivo com a senha), BACKUP_DIR; docker compose no diretorio deploy/.
# Restauracao testada periodicamente: deploy/restore.md
set -euo pipefail
cd "$(dirname "$0")"
: "${BACKUP_DIR:?defina BACKUP_DIR}"
: "${BACKUP_PASSPHRASE_FILE:?defina BACKUP_PASSPHRASE_FILE}"
umask 077
mkdir -p "$BACKUP_DIR"
arquivo="$BACKUP_DIR/gestao-$(date +%Y%m%d-%H%M%S).sql.gz.enc"
docker compose exec -T db pg_dump -U gestao -d gestao --no-owner \
  | gzip \
  | openssl enc -aes-256-cbc -pbkdf2 -salt -pass "file:$BACKUP_PASSPHRASE_FILE" -out "$arquivo"
# anexos e modelos de pecas (volume `storage`), tambem criptografados
docker compose exec -T backend tar -C /data -cz storage \
  | openssl enc -aes-256-cbc -pbkdf2 -salt -pass "file:$BACKUP_PASSPHRASE_FILE" -out "$BACKUP_DIR/storage-$(date +%Y%m%d-%H%M%S).tar.gz.enc"
# retencao: 30 dias locais (copiar tambem para fora do servidor)
find "$BACKUP_DIR" \( -name 'gestao-*.sql.gz.enc' -o -name 'storage-*.tar.gz.enc' \) -mtime +30 -delete
echo "Backup gerado: $arquivo"
