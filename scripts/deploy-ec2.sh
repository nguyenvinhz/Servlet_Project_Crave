#!/usr/bin/env bash
# Deploy a tested WAR to the existing EC2 Compose application, with rollback.
set -Eeuo pipefail

fail() { printf 'Deploy error: %s\n' "$*" >&2; exit 1; }
log() { printf '%s\n' "$*"; }

[[ $# -eq 1 ]] || fail 'Usage: DEPLOY_PATH=/home/ubuntu/crave bash scripts/deploy-ec2.sh /path/to/crave.war'
for dependency in docker curl python3 flock realpath mktemp install mv unzip sha256sum; do
  command -v "$dependency" >/dev/null || fail "Missing command: $dependency"
done

deploy_input=${DEPLOY_PATH:-/home/ubuntu/crave}
[[ $deploy_input == /* && -d $deploy_input ]] || fail 'DEPLOY_PATH must be an existing absolute directory.'
deploy_root=$(realpath -e -- "$deploy_input")
[[ $deploy_root != / ]] || fail 'DEPLOY_PATH cannot be the filesystem root.'
if [[ -n ${GITHUB_WORKSPACE:-} ]]; then
  workspace=$(realpath -e -- "$GITHUB_WORKSPACE")
  [[ $deploy_root != "$workspace" && $deploy_root != "$workspace/"* ]] || fail 'Production must be outside the runner checkout directory.'
fi

artifact=$(realpath -e -- "$1")
[[ -f $artifact && -s $artifact ]] || fail 'The WAR artifact is missing or empty.'
unzip -tq "$artifact" >/dev/null || fail 'The WAR artifact is not a valid ZIP archive.'

compose_input=${COMPOSE_FILE:-compose.yaml}
[[ $compose_input != /* ]] || fail 'COMPOSE_FILE must be relative to DEPLOY_PATH.'
compose_file=$(realpath -e -- "$deploy_root/$compose_input")
[[ -f $compose_file && $compose_file == "$deploy_root/"* ]] || fail 'Compose file must be inside DEPLOY_PATH.'
[[ -f $deploy_root/.env ]] || fail 'Existing production .env is required.'
service=${APP_SERVICE:-app}
[[ $service =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || fail 'Invalid APP_SERVICE.'

war_directory=$(realpath -e -- "$deploy_root/backend/target")
[[ $war_directory == "$deploy_root/"* ]] || fail 'WAR directory must be inside DEPLOY_PATH.'
war_path=$war_directory/crave.war
[[ -f $war_path && -s $war_path && ! -L $war_path ]] || fail 'Existing deployed backend/target/crave.war is required for rollback.'
[[ $artifact != "$war_path" ]] || fail 'Stage the incoming WAR outside the deployed WAR path.'

health_url=${HEALTH_URL:-http://127.0.0.1:8080/crave/api/health}
[[ $health_url =~ ^http://(127\.0\.0\.1|localhost):[0-9]+/crave/api/health$ ]] || fail 'HEALTH_URL must address the local /crave/api/health endpoint.'
health_timeout=${HEALTH_TIMEOUT_SECONDS:-180}
health_interval=${HEALTH_INTERVAL_SECONDS:-5}
[[ $health_timeout =~ ^[1-9][0-9]*$ && $health_interval =~ ^[1-9][0-9]*$ ]] || fail 'Health timeout and interval must be positive integers.'

docker_command=(docker)
if ! docker info >/dev/null 2>&1; then
  command -v sudo >/dev/null && sudo -n docker info >/dev/null 2>&1 || fail 'Runner needs Docker access or passwordless sudo docker.'
  docker_command=(sudo -n docker)
fi
compose=("${docker_command[@]}" compose --project-directory "$deploy_root" --file "$compose_file")
"${compose[@]}" version >/dev/null

# Protect against manual deployments or another runner touching the same WAR.
exec 9>"$deploy_root/.deploy.lock"
flock -w 300 9 || fail 'Timed out waiting for another deployment.'

# Read only service/mount metadata; never print the resolved secret environment.
"${compose[@]}" config --format json | python3 -c '
import json, os, sys
config = json.load(sys.stdin)
service = config.get("services", {}).get(sys.argv[1], {})
valid = any(
    volume.get("type") == "bind"
    and os.path.realpath(volume.get("source", "")) == os.path.realpath(sys.argv[2])
    and volume.get("target") == "/usr/local/tomcat/webapps/crave.war"
    for volume in service.get("volumes", [])
)
if not valid:
    sys.exit("App service must bind backend/target/crave.war to /usr/local/tomcat/webapps/crave.war.")
' "$service" "$war_path"

backup_directory=$deploy_root/.deploy-backups
mkdir -p -- "$backup_directory"
backup_directory=$(realpath -e -- "$backup_directory")
[[ $backup_directory == "$deploy_root/"* ]] || fail 'Backup directory must be inside DEPLOY_PATH.'
backup_path=$backup_directory/crave.war.previous
[[ ! -L $backup_path && ( ! -e $backup_path || -f $backup_path ) ]] || fail 'Backup WAR must be a regular file or absent.'
staging_file=''
rollback_needed=false

atomic_copy() {
  local source=$1 destination=$2
  staging_file=$(mktemp "$(dirname -- "$destination")/.crave-war.XXXXXX") || return 1
  if ! install -m 0644 -- "$source" "$staging_file" || ! mv -fT -- "$staging_file" "$destination"; then
    rm -f -- "$staging_file" || return 1
    staging_file=''
    return 1
  fi
  staging_file=''
}

recreate_app() {
  # Recreate is required: a file bind mount can retain the old inode on restart.
  "${compose[@]}" up -d --no-deps --force-recreate --no-build --pull never "$service"
}

wait_for_health() {
  local deadline=$((SECONDS + health_timeout)) response
  while (( SECONDS < deadline )); do
    if response=$(curl --fail --silent --show-error --connect-timeout 2 --max-time 5 "$health_url" 2>/dev/null) &&
      printf '%s' "$response" | python3 -c '
import json, sys
try:
    body = json.load(sys.stdin)
    data = body.get("data", {})
    healthy = body.get("success") is True and data.get("application") == "UP" and data.get("database") == "UP"
except (ValueError, AttributeError, TypeError):
    healthy = False
sys.exit(0 if healthy else 1)
'; then
      return 0
    fi
    sleep "$health_interval"
  done
  return 1
}

finish() {
  local status=$?
  trap - EXIT INT TERM
  if [[ $rollback_needed == true && $status -ne 0 ]]; then
    set +e
    log 'Deployment failed. Restoring the previous WAR.'
    if atomic_copy "$backup_path" "$war_path" && recreate_app && wait_for_health; then
      log 'Rollback completed; the previous application is healthy.'
    else
      log 'Rollback did not become healthy. Check the app container on EC2.' >&2
    fi
  fi
  if [[ -n $staging_file && -f $staging_file ]]; then
    rm -f -- "$staging_file"
  fi
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

atomic_copy "$war_path" "$backup_path"
rollback_needed=true
log 'Installing the tested WAR and recreating only the app service.'
atomic_copy "$artifact" "$war_path"
recreate_app
wait_for_health || fail "Application did not become healthy within ${health_timeout}s."
rollback_needed=false
log 'Deployment succeeded. Previous WAR saved in .deploy-backups/crave.war.previous.'
sha256sum "$war_path"
