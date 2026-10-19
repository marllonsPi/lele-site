# Restauracao de backup

Teste a restauracao em ambiente separado ao menos uma vez por mes. Backup que nunca foi restaurado nao e backup.

```bash
openssl enc -d -aes-256-cbc -pbkdf2 -pass file:$BACKUP_PASSPHRASE_FILE -in gestao-AAAAMMDD-HHMMSS.sql.gz.enc | gunzip \
  | docker compose exec -T db psql -U gestao -d gestao_restauracao
```
