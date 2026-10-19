#!/usr/bin/env bash
# Teste de restauracao NA VM, em container postgres:16-alpine descartavel (sem rede, 128 MB, sem porta).
# Usa o backup mais recente de ~/backups/crispim; nao toca no banco de producao. Imprime so contagens.
set -euo pipefail
cd ~/crispim-campos/deploy
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups/crispim}"
PASS="${BACKUP_PASSPHRASE_FILE:-$HOME/.crispim-backup.pass}"
dump=$(ls -1t "$BACKUP_DIR"/gestao-2*.sql.gz.enc | head -1)
sto=$(ls -1t "$BACKUP_DIR"/storage-*.tar.gz.enc | head -1)
echo "Dump: $(basename "$dump")  Storage: $(basename "$sto")"
T=restauracao-tmp; TMPD=$(mktemp -d)
cleanup() { docker rm -f "$T" >/dev/null 2>&1 || true; rm -rf "$TMPD"; }
trap cleanup EXIT
docker run -d --name "$T" --network none --memory 128m -e POSTGRES_PASSWORD="$(openssl rand -hex 8)" postgres:16-alpine >/dev/null
for i in $(seq 1 30); do docker exec "$T" pg_isready -U postgres >/dev/null 2>&1 && break; sleep 2; done
docker exec "$T" psql -q -U postgres -c "create role gestao login" -c "create database gestao owner gestao"
openssl enc -d -aes-256-cbc -pbkdf2 -pass "file:$PASS" -in "$dump" | gunzip \
  | docker exec -i "$T" psql -q -v ON_ERROR_STOP=1 -U gestao -d gestao >/dev/null
docker exec -i "$T" psql -At -U gestao -d gestao <<'SQL'
select 'tabelas=' || count(*) from information_schema.tables where table_schema='public';
select 'flyway_ok=' || count(*) || ' ultima=' || max(version::int) from flyway_schema_history where success;
select 'triggers=' || count(*) from information_schema.triggers where trigger_schema='public';
select 'usuarios=' || count(*) from usuario;
select 'auditoria=' || count(*) from log_auditoria;
SQL
openssl enc -d -aes-256-cbc -pbkdf2 -pass "file:$PASS" -in "$sto" | gunzip | tar -C "$TMPD" -x
echo "storage restaurado: $(find "$TMPD/storage" -type f | wc -l) arquivo(s), $(du -sk "$TMPD" | cut -f1) KiB"
echo "RESTAURACAO OK"
