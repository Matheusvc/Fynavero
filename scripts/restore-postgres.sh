#!/usr/bin/env sh
# Restaura um backup criptografado num banco PostgreSQL.
# ATENÇÃO: --clean apaga as tabelas do banco de destino antes de recriá-las. Teste primeiro num banco novo.
# Uso: TARGET_DATABASE_URL='postgres://usuario:senha@host:5432/fynavero_teste' ./scripts/restore-postgres.sh fynavero-AAAA-MM-DD.dump.gpg
set -eu
FILE="${1:?Informe o arquivo .dump.gpg}"
: "${TARGET_DATABASE_URL:?Defina TARGET_DATABASE_URL com o banco de destino}"
gpg --decrypt "$FILE" | pg_restore --clean --if-exists --no-owner --no-privileges --dbname "$TARGET_DATABASE_URL"
echo "Restauração concluída."
