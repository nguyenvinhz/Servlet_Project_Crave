#!/usr/bin/env bash
# Start the tested WAR with the runner's disposable MySQL and exercise real HTTP/JSP/session behavior.
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
[[ -s backend/target/crave.war ]] || { printf 'Missing tested WAR.\n' >&2; exit 1; }
: "${DB_URL:?}" "${DB_USER:?}" "${DB_PASSWORD:?}"
artifacts=${SMOKE_ARTIFACTS_DIR:-${RUNNER_TEMP:-/tmp}/crave-runtime-smoke}
mkdir -p "$artifacts"
container="crave-ci-tomcat-${GITHUB_RUN_ID:-$$}-${GITHUB_RUN_ATTEMPT:-1}"
cleanup() {
  local status=$?
  trap - EXIT
  docker logs "$container" > "$artifacts/tomcat.log" 2>&1 || true
  docker rm -f "$container" >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT

docker run --detach --name "$container" --network host \
  --env DB_URL --env DB_USER --env DB_PASSWORD --env DB_FAIL_FAST=true \
  --env TZ=Asia/Ho_Chi_Minh \
  --mount "type=bind,source=$PWD/backend/target/crave.war,target=/usr/local/tomcat/webapps/crave.war,readonly" \
  "${TOMCAT_IMAGE:-tomcat:10.1-jdk17-temurin}" >/dev/null

ready=false
for ((attempt=0; attempt<90; attempt++)); do
  if curl --fail --silent --max-time 3 http://127.0.0.1:8080/crave/api/health > "$artifacts/health.json" &&
    python3 -c 'import json,sys; b=json.load(sys.stdin); sys.exit(0 if b.get("success") is True and b.get("data",{}).get("application")=="UP" and b.get("data",{}).get("database")=="UP" else 1)' < "$artifacts/health.json"; then
    ready=true
    break
  fi
  sleep 2
done
[[ $ready == true ]] || { printf 'Tomcat/MySQL did not become healthy.\n' >&2; exit 1; }

SMOKE_BASE_URL=http://127.0.0.1:8080/crave SMOKE_ARTIFACTS_DIR="$artifacts" \
  node frontend/tests/runtime-smoke.cjs
