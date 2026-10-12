#!/usr/bin/env bash
# Recreate only an explicitly identified, disposable CI database.
set -Eeuo pipefail
[[ ${CI_DISPOSABLE_DATABASE:-} == true ]] || {
  printf 'Set CI_DISPOSABLE_DATABASE=true only for a disposable test server.\n' >&2
  exit 1
}
cd "$(dirname "$0")/../.."
source scripts/ci/mysql-client.sh

mysql_root mysql < database/schema.sql
mysql_root crave < database/seed.sql
mysql_root crave < scripts/ci/application-user.sql

if [[ -n ${MYSQL_CONTAINER_ID:-} ]]; then
  docker exec -i -e MYSQL_PWD=ci-app-password "$MYSQL_CONTAINER_ID" \
    mysql --protocol=TCP --host=127.0.0.1 --user=crave_app --execute='SELECT 1' crave
else
  MYSQL_PWD=ci-app-password "${MYSQL_BIN:-mysql}" --protocol=TCP \
    --host="${MYSQL_HOST:?}" --port="${MYSQL_PORT:?}" --user=crave_app --execute='SELECT 1' crave
fi
