#!/usr/bin/env bash
set -euo pipefail
umask 077

# El SQL compartido fija estos roles. No aceptar otros nombres silenciosamente.
[[ "$APP_DB_USER" == oposicionapp_app && "$FLYWAY_DB_USER" == oposicionapp_migrator ]] || {
  printf '%s\n' 'Los nombres de los roles no coinciden con el bootstrap.' >&2; exit 1;
}
[[ "$POSTGRES_USER" != "$APP_DB_USER" && "$POSTGRES_USER" != "$FLYWAY_DB_USER" ]] || exit 1

for secret in postgres_password app_db_password flyway_db_password; do
  file="/run/secrets/$secret"
  [[ -s "$file" ]] || { printf '%s\n' 'Falta un archivo de contraseña no vacío.' >&2; exit 1; }
  # Una contraseña por archivo, sin saltos de línea internos ni retorno de carro.
  [[ $(awk 'END {print NR}' "$file") -eq 1 ]] && ! grep -q $'\r' "$file" || exit 1
done
admin_password=$(cat /run/secrets/postgres_password)
app_password=$(cat /run/secrets/app_db_password)
migrator_password=$(cat /run/secrets/flyway_db_password)
[[ -n "$admin_password" && -n "$app_password" && -n "$migrator_password" ]] || exit 1
[[ "$admin_password" != "$app_password" && "$admin_password" != "$migrator_password" && "$app_password" != "$migrator_password" ]] || {
  printf '%s\n' 'Las tres contraseñas de PostgreSQL deben ser diferentes.' >&2; exit 1;
}

# .pgpass evita incluir la contraseña administrativa en argumentos o en SQL.
escaped_password=$(printf '%s' "$admin_password" | sed 's/[\\:]/\\&/g')
printf '*:*:*:*:%s\n' "$escaped_password" > /tmp/bootstrap.pgpass
export PGPASSFILE=/tmp/bootstrap.pgpass
trap 'rm -f /tmp/bootstrap.pgpass' EXIT
unset admin_password escaped_password

psql -X --no-password -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v POSTGRES_DB="$POSTGRES_DB" \
  -f /opt/bootstrap/postgresql_roles.sql >/dev/null

# \password cifra en el cliente; el servidor no recibe una sentencia con texto plano.
printf '%s\n%s\n' "$app_password" "$app_password" |
  psql -X --no-password -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" \
    -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\password oposicionapp_app' >/dev/null
printf '%s\n%s\n' "$migrator_password" "$migrator_password" |
  psql -X --no-password -v ON_ERROR_STOP=1 -h "$PGHOST" -p "$PGPORT" \
    -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\password oposicionapp_migrator' >/dev/null
unset app_password migrator_password
printf '%s\n' 'Bootstrap de roles completado; repetir tras la primera migración para retirar CREATE temporal.'
