#!/usr/bin/env sh
# Backup manual do PostgreSQL de produção, criptografado (o mesmo formato do backup diário do GitHub).
# Uso: DATABASE_BACKUP_URL='postgres://usuario:senha@host:5432/fynavero?sslmode=require' ./scripts/backup-postgres.sh
# A frase de criptografia é pedida no terminal (ou use BACKUP_PASSPHRASE).
set -eu
: "${DATABASE_BACKUP_URL:?Defina DATABASE_BACKUP_URL com a URL externa do banco}"
FILE="fynavero-$(date -u +%Y-%m-%d-%H%M).dump.gpg"
if [ -n "${BACKUP_PASSPHRASE:-}" ]; then
  pg_dump --format=custom --no-owner --no-privileges "$DATABASE_BACKUP_URL" \
    | gpg --batch --yes --symmetric --cipher-algo AES256 --passphrase "$BACKUP_PASSPHRASE" -o "$FILE"
else
  pg_dump --format=custom --no-owner --no-privileges "$DATABASE_BACKUP_URL" | gpg --symmetric --cipher-algo AES256 -o "$FILE"
fi
echo "Backup salvo em $FILE"
