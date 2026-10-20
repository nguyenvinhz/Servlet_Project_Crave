#!/usr/bin/env bash
# Deploy a tested WAR and additive payment/account migrations to the existing EC2 application.
set -Eeuo pipefail
umask 077

fail() { printf 'Deploy error: %s\n' "$*" >&2; exit 1; }
log() { printf '%s\n' "$*"; }

[[ $# -eq 1 ]] || fail 'Usage: DEPLOY_PATH=/home/ubuntu/crave bash scripts/deploy-ec2.sh /path/to/crave.war'
for dependency in docker curl python3 flock realpath mktemp install mv unzip sha256sum chmod env cat; do
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

script_file=$(realpath -e -- "${BASH_SOURCE[0]}")
source_root=$(dirname -- "$(dirname -- "$script_file")")
migration_files=()
for migration_name in 2026-10-13-payos-payment.sql 2026-10-13-email-verification.sql 2026-10-15-delivery-location.sql; do
  migration_input=$source_root/database/migrations/$migration_name
  [[ -f $migration_input && -s $migration_input && ! -L $migration_input ]] || fail 'A release migration is missing or invalid.'
  migration_file=$(realpath -e -- "$migration_input")
  [[ $migration_file == "$source_root/database/migrations/$migration_name" ]] || fail 'Migration must belong to this release checkout.'
  migration_files+=("$migration_file")
done

compose_input=${COMPOSE_FILE:-compose.yaml}
[[ $compose_input != /* ]] || fail 'COMPOSE_FILE must be relative to DEPLOY_PATH.'
compose_file=$(realpath -e -- "$deploy_root/$compose_input")
[[ -f $compose_file && $compose_file == "$deploy_root/"* ]] || fail 'Compose file must be inside DEPLOY_PATH.'
[[ -f $deploy_root/.env ]] || fail 'Existing production .env is required.'
service=${APP_SERVICE:-app}
db_service=${DB_SERVICE:-mysql}
[[ $service =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || fail 'Invalid APP_SERVICE.'
[[ $db_service =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ && $db_service != "$service" ]] || fail 'Invalid DB_SERVICE.'

war_directory=$(realpath -e -- "$deploy_root/target" 2>/dev/null || realpath -e -- "$deploy_root/backend/target")
[[ $war_directory == "$deploy_root/"* ]] || fail 'WAR directory must be inside DEPLOY_PATH.'
war_path=$war_directory/crave.war
[[ -f $war_path && -s $war_path && ! -L $war_path ]] || fail 'Existing deployed crave.war is required for rollback.'
[[ $artifact != "$war_path" ]] || fail 'Stage the incoming WAR outside the deployed WAR path.'

health_url=${HEALTH_URL:-http://127.0.0.1:8080/crave/health}
[[ $health_url =~ ^http://(127\.0\.0\.1|localhost):[0-9]+(/crave)?/(api/)?health$ ]] || fail 'HEALTH_URL must address a local root or /crave health endpoint.'
application_context=${BASH_REMATCH[2]}
war_target=/usr/local/tomcat/webapps/crave.war
if [[ -z $application_context ]]; then
  war_target=/usr/local/tomcat/webapps/ROOT.war
fi
health_timeout=${HEALTH_TIMEOUT_SECONDS:-180}
health_interval=${HEALTH_INTERVAL_SECONDS:-5}
[[ $health_timeout =~ ^[1-9][0-9]*$ && $health_interval =~ ^[1-9][0-9]*$ ]] || fail 'Health timeout and interval must be positive integers.'

override_path=$deploy_root/.payos.compose.json
[[ ! -L $override_path && ( ! -e $override_path || -f $override_path ) ]] || fail 'payOS override must be a regular file or absent.'
lock_path=$deploy_root/.deploy.lock
[[ ! -L $lock_path && ( ! -e $lock_path || -f $lock_path ) ]] || fail 'Deployment lock must be a regular file or absent.'

docker_command=(docker)
if ! docker info >/dev/null 2>&1; then
  command -v sudo >/dev/null && sudo -n docker info >/dev/null 2>&1 || fail 'Runner needs Docker access or passwordless sudo docker.'
  docker_command=(sudo -n docker)
fi
# New workflow secrets must not change interpolation in the existing base Compose file.
# The private override supplies literal payOS values; .env keeps all existing DB settings.
base_compose=(env -u PAYOS_CLIENT_ID -u PAYOS_API_KEY -u PAYOS_CHECKSUM_KEY -u PAYOS_PUBLIC_BASE_URL
  -u EMAIL_SMTP_HOST -u EMAIL_SMTP_PORT -u EMAIL_SMTP_USERNAME -u EMAIL_SMTP_PASSWORD
  -u EMAIL_FROM -u EMAIL_SMTP_STARTTLS -u EMAIL_OTP_SECRET
  -u CRAVE_STORE_ADDRESS -u CRAVE_STORE_LATITUDE -u CRAVE_STORE_LONGITUDE -u CRAVE_GEOCODER_URL
  "${docker_command[@]}" compose --project-directory "$deploy_root" --profile production --file "$compose_file")
compose=("${base_compose[@]}")
"${base_compose[@]}" version >/dev/null

# Protect manual deployments and prevent a lock symlink from truncating another file.
exec 9>"$lock_path"
flock -w 300 9 || fail 'Timed out waiting for another deployment.'

backup_directory=$deploy_root/.deploy-backups
mkdir -p -- "$backup_directory"
backup_directory=$(realpath -e -- "$backup_directory")
[[ $backup_directory == "$deploy_root/"* ]] || fail 'Backup directory must be inside DEPLOY_PATH.'
chmod 0700 -- "$backup_directory"
backup_path=$backup_directory/crave.war.previous
override_backup=$backup_directory/payos.compose.previous.json
override_absent=$backup_directory/payos.compose.previous.absent
for destination in "$backup_path" "$override_backup" "$override_absent"; do
  [[ ! -L $destination && ( ! -e $destination || -f $destination ) ]] || fail 'Backup destinations must be regular files or absent.'
done
staging_file=''
candidate_file=''
rollback_needed=false
previous_override_exists=false

atomic_copy() {
  local source=$1 destination=$2 mode=${3:-0644} prefix=${4:-.crave-war}
  staging_file=$(mktemp "$(dirname -- "$destination")/$prefix.XXXXXX") || return 1
  if ! install -m "$mode" -- "$source" "$staging_file" || ! mv -fT -- "$staging_file" "$destination"; then
    rm -f -- "$staging_file" || return 1
    staging_file=''
    return 1
  fi
  staging_file=''
}

recreate_app() {
  # A file bind mount can retain the old inode on restart, so recreate only the app.
  "${compose[@]}" up -d --no-deps --force-recreate --no-build --pull never "$service"
}

wait_for_health() {
  local rollback=${1:-false} deadline=$((SECONDS + health_timeout)) response endpoint
  local endpoints=("$health_url")
  if [[ $rollback == true && $health_url != */api/health ]]; then
    endpoints+=("${health_url%/health}/api/health")
  fi
  while (( SECONDS < deadline )); do
    for endpoint in "${endpoints[@]}"; do
      if response=$(curl --fail --silent --show-error --connect-timeout 2 --max-time 5 "$endpoint" 2>/dev/null) &&
        printf '%s' "$response" | python3 -c '
import json, sys
try:
    response = sys.stdin.read().strip()
    if response.startswith("{"):
        # Keep rollback compatible with the previous WAR health response.
        body = json.loads(response)
        data = body.get("data", {})
        healthy = body.get("success") is True and data.get("application") == "UP" and data.get("database") == "UP"
    else:
        lines = response.splitlines()
        data = dict(line.split("=", 1) for line in lines)
        healthy = len(lines) == 2 and data == {"application": "UP", "database": "UP"}
except (ValueError, AttributeError, TypeError):
    healthy = False
sys.exit(0 if healthy else 1)
'; then
        return 0
      fi
    done
    sleep "$health_interval"
  done
  return 1
}

restore_override() {
  if [[ $previous_override_exists == true ]]; then
    atomic_copy "$override_backup" "$override_path" 0600 .payos-restore || return 1
    compose=("${base_compose[@]}" --file "$override_path")
  else
    rm -f -- "$override_path" || return 1
    compose=("${base_compose[@]}")
  fi
}

finish() {
  local status=$?
  trap - EXIT INT TERM
  if [[ $rollback_needed == true && $status -ne 0 ]]; then
    set +e
    log 'Deployment failed. Restoring the previous WAR and application configuration.'
    if atomic_copy "$backup_path" "$war_path" && restore_override && recreate_app && wait_for_health true; then
      log 'Rollback completed; the previous application is healthy.'
    else
      log 'Rollback did not become healthy. Check the app container on EC2.' >&2
    fi
    # Additive payment and email-verification tables remain intact during rollback.
  fi
  if [[ -n $staging_file && -f $staging_file ]]; then rm -f -- "$staging_file"; fi
  if [[ -n $candidate_file && -f $candidate_file ]]; then rm -f -- "$candidate_file"; fi
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

candidate_file=$(mktemp "$deploy_root/.payos-candidate.XXXXXX")
# JSON is a valid Compose YAML document. Escape dollar signs to disable interpolation;
# no credential ever appears in a command argument, generated log, or base .env.
python3 - "$service" "$candidate_file" "$application_context" <<'PY'
import ipaddress, json, os, re, sys
from urllib.parse import urlsplit

def fail(message):
    sys.exit("Deploy error: " + message)

keys = ("PAYOS_CLIENT_ID", "PAYOS_API_KEY", "PAYOS_CHECKSUM_KEY")
environment = {}
for name in keys:
    value = os.environ.get(name, "")
    if not value or len(value) > 512 or any(ord(char) < 33 or ord(char) > 126 for char in value):
        fail("All production payOS credentials are required and must be valid.")
    environment[name] = value
raw_url = os.environ.get("PAYOS_PUBLIC_BASE_URL", "")
if any(char.isspace() for char in raw_url) or "?" in raw_url or "#" in raw_url:
    fail("PAYOS_PUBLIC_BASE_URL must not contain whitespace, a query, or a fragment.")
try:
    parsed = urlsplit(raw_url)
    host = (parsed.hostname or "").rstrip(".").lower()
    port = parsed.port
    valid = (
        bool(raw_url) and len(raw_url) <= 2048 and parsed.scheme in ("http", "https")
        and bool(host) and parsed.username is None and parsed.password is None
        and not parsed.query and not parsed.fragment and parsed.path in ("", "/", "/crave", "/crave/")
        and host not in ("localhost", "localhost.localdomain") and not host.endswith(".localhost")
        and not host.endswith(".local") and (port is None or 1 <= port <= 65535)
    )
    if not valid:
        fail("PAYOS_PUBLIC_BASE_URL must be a public HTTP(S) URL for root or /crave.")
    if parsed.path.rstrip("/") != sys.argv[3]:
        fail("PAYOS_PUBLIC_BASE_URL context must match HEALTH_URL.")
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        labels = host.split(".")
        if (len(host) > 253 or "." not in host
                or all(re.fullmatch(r"(?:[0-9]+|0x[0-9a-f]+)", label) for label in labels)
                or not all(re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label) for label in labels)):
            fail("PAYOS_PUBLIC_BASE_URL must use a public host.")
    else:
        if not address.is_global or address.is_multicast:
            fail("Production payOS callbacks cannot use local, private, or multicast addresses.")
    environment["PAYOS_PUBLIC_BASE_URL"] = raw_url.rstrip("/")
except (ValueError, TypeError):
    fail("PAYOS_PUBLIC_BASE_URL is invalid.")

mail_defaults = {"EMAIL_SMTP_HOST": "smtp.gmail.com", "EMAIL_SMTP_PORT": "587", "EMAIL_SMTP_STARTTLS": "true"}
for name in ("EMAIL_SMTP_HOST", "EMAIL_SMTP_PORT", "EMAIL_SMTP_USERNAME", "EMAIL_SMTP_PASSWORD", "EMAIL_FROM", "EMAIL_SMTP_STARTTLS", "EMAIL_OTP_SECRET"):
    value = os.environ.get(name, mail_defaults.get(name, ""))
    if not value or len(value) > 512 or any(ord(char) < 32 or ord(char) > 126 for char in value):
        fail("All production email settings are required and must be valid.")
    environment[name] = value
if not re.fullmatch(r"[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?", environment["EMAIL_SMTP_HOST"]):
    fail("EMAIL_SMTP_HOST is invalid.")
try:
    mail_port = int(environment["EMAIL_SMTP_PORT"])
except ValueError:
    fail("EMAIL_SMTP_PORT is invalid.")
if not 1 <= mail_port <= 65535 or environment["EMAIL_SMTP_STARTTLS"].lower() != "true":
    fail("Production email requires STARTTLS and a valid SMTP port.")
if environment["EMAIL_SMTP_HOST"].lower() == "smtp.gmail.com" and mail_port != 587:
    fail("Gmail SMTP must use port 587 with STARTTLS.")
for name in ("EMAIL_SMTP_USERNAME", "EMAIL_FROM"):
    if not re.fullmatch(r"[^@\s<>]+@[^@\s<>]+\.[^@\s<>]+", environment[name]):
        fail("Production email addresses must be valid.")
if len(environment["EMAIL_OTP_SECRET"]) < 32 or any(char.isspace() for char in environment["EMAIL_OTP_SECRET"]):
    fail("EMAIL_OTP_SECRET must contain at least 32 non-whitespace characters.")

if bool(os.environ.get("CRAVE_STORE_LATITUDE", "").strip()) != bool(os.environ.get("CRAVE_STORE_LONGITUDE", "").strip()):
    fail("Store latitude and longitude must be configured together.")
for name in ("CRAVE_STORE_ADDRESS", "CRAVE_STORE_LATITUDE", "CRAVE_STORE_LONGITUDE", "CRAVE_GEOCODER_URL"):
    value = os.environ.get(name, "").strip()
    if value:
        if name == "CRAVE_STORE_ADDRESS":
            if len(value) > 255 or any(ord(char) < 32 for char in value):
                fail("CRAVE_STORE_ADDRESS is invalid.")
        elif name == "CRAVE_GEOCODER_URL":
            try:
                endpoint = urlsplit(value)
                if endpoint.scheme not in ("http", "https") or not endpoint.hostname or endpoint.username is not None or endpoint.password is not None or endpoint.fragment:
                    fail("CRAVE_GEOCODER_URL is invalid.")
            except ValueError:
                fail("CRAVE_GEOCODER_URL is invalid.")
        else:
            try:
                coordinate = float(value)
                limit = 90 if name == "CRAVE_STORE_LATITUDE" else 180
                if not -limit <= coordinate <= limit:
                    fail("Store coordinates are invalid.")
            except ValueError:
                fail("Store coordinates must be numeric.")
        environment[name] = value

escaped = {name: value.replace("$", "$$") for name, value in environment.items()}
try:
    with open(sys.argv[2], "w", encoding="utf-8") as output:
        json.dump({"services": {sys.argv[1]: {"environment": escaped}}}, output, ensure_ascii=True)
        output.write("\n")
except OSError:
    fail("Cannot stage production payOS configuration.")
PY
chmod 0600 -- "$candidate_file"
candidate_compose=("${base_compose[@]}" --file "$candidate_file")

# Consume resolved configuration privately. Validate only named services, mount, and payOS settings.
"${candidate_compose[@]}" config --format json 2>/dev/null | python3 -c '
import json, os, sys
try:
    config = json.load(sys.stdin)
    services = config.get("services", {})
    service = services.get(sys.argv[1], {})
    valid = any(
        volume.get("type") == "bind"
        and os.path.realpath(volume.get("source", "")) == os.path.realpath(sys.argv[2])
        and volume.get("target") == sys.argv[5]
        for volume in service.get("volumes", [])
    )
    if not valid:
        sys.exit("App service must bind crave.war to " + sys.argv[5] + " to match HEALTH_URL.")
    if not services.get(sys.argv[3]):
        sys.exit("The existing database service is missing from Compose.")
    with open(sys.argv[4], encoding="utf-8") as source:
        expected = json.load(source)["services"][sys.argv[1]]["environment"]
    environment = service.get("environment", {})
    # Compose config serializes literal dollars as $$, matching the staged escaped values.
    if any(environment.get(name) != value for name, value in expected.items()):
        sys.exit("Compose must pass all production payment and email settings to the app.")
except (ValueError, KeyError, TypeError, AttributeError, OSError):
    sys.exit("Cannot validate production Compose configuration.")
' "$service" "$war_path" "$db_service" "$candidate_file" "$war_target"

# The old WAR keeps serving while this idempotent, additive migration runs.
# The password is read inside the existing DB container, not passed in argv or runner logs.
log 'Applying additive payment, email-verification, and delivery-location migrations to the existing database service.'
migration_status=0
cat -- "${migration_files[@]}" | "${base_compose[@]}" exec -T "$db_service" sh -c \
  'test -n "${MYSQL_ROOT_PASSWORD:-}" || { printf "%s\n" "Database migration credentials are unavailable." >&2; exit 1; }; MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --default-character-set=utf8mb4 --database=crave' \
  >/dev/null 2>&1 || migration_status=$?
if (( migration_status != 0 )); then
  log 'Database migration failed; the existing application was left running.' >&2
  exit "$migration_status"
fi

atomic_copy "$war_path" "$backup_path"
if [[ -f $override_path ]]; then
  previous_override_exists=true
  atomic_copy "$override_path" "$override_backup" 0600 .payos-backup
  rm -f -- "$override_absent"
else
  # Persist absence as well, so a manual rollback can restore the original Compose invocation.
  staging_file=$(mktemp "$backup_directory/.payos-absence.XXXXXX")
  printf 'absent\n' > "$staging_file"
  chmod 0600 -- "$staging_file"
  mv -fT -- "$staging_file" "$override_absent"
  staging_file=''
  rm -f -- "$override_backup"
fi
rollback_needed=true
log 'Installing the tested WAR and recreating only the app service.'
atomic_copy "$candidate_file" "$override_path" 0600 .payos-activate
compose=("${base_compose[@]}" --file "$override_path")
atomic_copy "$artifact" "$war_path"
recreate_app
wait_for_health || fail "Application did not become healthy within ${health_timeout}s."
rollback_needed=false
log 'Deployment succeeded. Previous WAR and application configuration saved in .deploy-backups.'
sha256sum "$war_path"
