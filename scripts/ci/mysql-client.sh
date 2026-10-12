#!/usr/bin/env bash
# Source this file from CI database checks; never target a production database.
set -Eeuo pipefail

mysql_root() {
  if [[ -n ${MYSQL_CONTAINER_ID:-} ]]; then
    docker exec -i -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?}" "$MYSQL_CONTAINER_ID" \
      mysql --user=root --default-character-set=utf8mb4 --batch --skip-column-names "$@"
  else
    # Enables the same checks against an explicitly supplied local test server.
    MYSQL_PWD=${MYSQL_ROOT_PASSWORD:?} "${MYSQL_BIN:-mysql}" \
      --host="${MYSQL_HOST:?}" --port="${MYSQL_PORT:?}" --protocol=TCP \
      --user=root --default-character-set=utf8mb4 --batch --skip-column-names "$@"
  fi
}

mysql_dump() {
  local args=(--user=root --no-create-info --skip-triggers --skip-comments
    --skip-extended-insert --order-by-primary --set-gtid-purged=OFF --no-tablespaces crave)
  if [[ -n ${MYSQL_CONTAINER_ID:-} ]]; then
    docker exec -i -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?}" "$MYSQL_CONTAINER_ID" mysqldump "${args[@]}"
  else
    MYSQL_PWD=${MYSQL_ROOT_PASSWORD:?} "${MYSQL_DUMP_BIN:-mysqldump}" \
      --host="${MYSQL_HOST:?}" --port="${MYSQL_PORT:?}" --protocol=TCP "${args[@]}"
  fi
}
