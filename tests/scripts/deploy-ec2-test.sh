#!/usr/bin/env bash
# Exercise deployment and rollback with isolated fixtures; never call real Docker.
set -Eeuo pipefail

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
deploy_script=$(realpath -- "$script_directory/../../scripts/deploy-ec2.sh")
test_payos_migration=$(realpath -- "$script_directory/../../database/migrations/2026-10-13-payos-payment.sql")
test_email_migration=$(realpath -- "$script_directory/../../database/migrations/2026-10-13-email-verification.sql")
test_delivery_migration=$(realpath -- "$script_directory/../../database/migrations/2026-10-15-delivery-location.sql")
for dependency in bash python3 cmp mktemp install sha256sum unzip; do
  command -v "$dependency" >/dev/null || { printf 'Missing test dependency: %s\n' "$dependency" >&2; exit 1; }
done
real_install=$(command -v install)
temp_directory=$(realpath -- "${TMPDIR:-/tmp}")
test_root=$(mktemp -d "$temp_directory/crave-deploy-test.XXXXXX")
[[ $test_root == "$temp_directory/"crave-deploy-test.* ]] || exit 1
test_migration=$test_root/release-migrations.sql
cat -- "$test_payos_migration" "$test_email_migration" "$test_delivery_migration" > "$test_migration"
cleanup() {
  local status=$?
  if [[ $status -ne 0 && -n ${case_directory:-} ]]; then
    printf 'Failed fixture: %s\n' "$case_directory" >&2
    [[ ! -f $case_directory/deploy.log ]] || cat "$case_directory/deploy.log" >&2
  fi
  if [[ ${KEEP_DEPLOY_TEST_FIXTURES:-0} == 1 ]]; then
    printf 'Deployment test fixtures retained: %s\n' "$test_root"
  elif [[ $test_root == "$temp_directory/"crave-deploy-test.* ]]; then
    rm -rf -- "$test_root"
  fi
  exit "$status"
}
trap cleanup EXIT
mkdir -p -- "$test_root/bin" "$test_root/workspace"

cat > "$test_root/bin/compose-fixture.py" <<'PY'
import copy, json, os, re, sys

base, *overlays = sys.argv[1:]
deploy_directory = os.path.dirname(os.path.abspath(base))
case_directory = os.path.dirname(deploy_directory)
with open(base, encoding="utf-8") as source:
    config = json.load(source)
config = copy.deepcopy(config)
local_settings = {}
with open(os.path.join(deploy_directory, ".env"), encoding="utf-8") as source:
    for line in source:
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.rstrip("\n").split("=", 1)
            local_settings[key] = value

def interpolate(value):
    if not isinstance(value, str):
        return value
    def expand(match):
        if match.group(0) == "$$":
            return "$"
        expression = match.group(1) or match.group(2)
        if ":-" in expression:
            name, fallback = expression.split(":-", 1)
            return os.environ.get(name) or local_settings.get(name) or fallback
        return os.environ.get(expression, local_settings.get(expression, ""))
    return re.sub(r"\$\$|\$\{([^}]+)\}|\$([A-Za-z_][A-Za-z0-9_]*)", expand, value)

for service in config.get("services", {}).values():
    environment = service.get("environment", {})
    service["environment"] = {key: interpolate(value) for key, value in environment.items()}
for overlay in overlays:
    with open(overlay, encoding="utf-8") as source:
        extra = json.load(source)
    for name, additions in extra.get("services", {}).items():
        service = config.setdefault("services", {}).setdefault(name, {})
        for field, value in additions.items():
            if field == "environment":
                service.setdefault(field, {}).update({key: interpolate(item) for key, item in value.items()})
            else:
                service[field] = copy.deepcopy(value)
service = config["services"].get("app", {})
for volume in service.get("volumes", []):
    volume["source"] = os.path.realpath(os.path.join(deploy_directory, volume["source"]))
mode = os.environ["TEST_CONFIG"]
if mode == "missing-bind":
    service["volumes"] = []
if mode == "missing-service":
    config["services"]["different-app"] = config["services"].pop("app")
with open(os.path.join(case_directory, "resolved-compose.json"), "w", encoding="utf-8") as target:
    json.dump(config, target)
serialized = json.dumps(config)
# Compose v5 re-escapes dollar signs when serializing config, including JSON.
# Runtime values remain the decoded strings saved above for recreation assertions.
if mode != "raw-dollar-json":
    serialized = serialized.replace("$", "$$")
with open(os.path.join(case_directory, "serialized-compose.json"), "w", encoding="utf-8") as target:
    target.write(serialized)
print(serialized)
PY

cat > "$test_root/bin/docker" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$TEST_CASE_DIRECTORY/docker.calls"
[[ $1 != info ]] || exit 0
[[ $1 == compose ]] || exit 90
shift
[[ $1 == --project-directory && $2 == "$TEST_DEPLOY_DIRECTORY" ]] || exit 91
shift 2
[[ $1 == --file && $2 == "$TEST_DEPLOY_DIRECTORY/compose.yaml" ]] || exit 92
compose_files=()
while [[ ${1:-} == --file ]]; do
  [[ $# -ge 2 && -f $2 && ! -L $2 && $2 == "$TEST_DEPLOY_DIRECTORY/"* ]] || exit 96
  compose_files+=("$2")
  shift 2
done
case "$1" in
  version) printf 'Docker Compose fixture\n' ;;
  config)
    if [[ $TEST_CONFIG == secret-stderr-failure ]]; then
      printf 'fixture-api-key and existing-database-fixture-password from a Docker diagnostic\n' >&2
      exit 37
    fi
    python3 "$(dirname -- "$0")/compose-fixture.py" "${compose_files[@]}"
    ;;
  exec)
    [[ $# == 6 && $2 == -T && $3 == "$TEST_DATABASE_SERVICE" && $4 == sh && $5 == -c ]] || exit 97
    [[ $6 == *MYSQL_ROOT_PASSWORD* && $6 == *MYSQL_PWD* && $6 == *mysql* && $6 == *crave* ]] || exit 98
    [[ ${#compose_files[@]} == 1 ]] || exit 99
    cat > "$TEST_CASE_DIRECTORY/migration.sql"
    cmp -s -- "$TEST_MIGRATION" "$TEST_CASE_DIRECTORY/migration.sql" || exit 100
    grep -Fq 'CREATE TABLE IF NOT EXISTS payos_payment_link' "$TEST_CASE_DIRECTORY/migration.sql" || exit 101
    grep -Fq 'CREATE TABLE IF NOT EXISTS email_verification_challenges' "$TEST_CASE_DIRECTORY/migration.sql" || exit 101
    grep -Fq 'ADD COLUMN delivery_latitude' "$TEST_CASE_DIRECTORY/migration.sql" || exit 101
    grep -Fq 'ADD COLUMN delivery_longitude' "$TEST_CASE_DIRECTORY/migration.sql" || exit 101
    grep -Fq 'ADD COLUMN delivery_distance_km' "$TEST_CASE_DIRECTORY/migration.sql" || exit 101
    cmp -s -- "$TEST_CASE_DIRECTORY/reference/old.war" "$TEST_DEPLOY_DIRECTORY/backend/target/crave.war" || exit 102
    [[ ! -e $TEST_CASE_DIRECTORY/recreate.count ]] || exit 103
    printf 'migration\n' >> "$TEST_CASE_DIRECTORY/operations.log"
    if [[ $TEST_MIGRATION_FAIL != 0 ]]; then
      printf 'fixture-api-key from a database diagnostic\n' >&2
      printf 'existing-database-fixture-password from database stdout\n'
      exit 31
    fi
    printf 'additive migration applied\n' > "$TEST_CASE_DIRECTORY/migration.applied"
    ;;
  up)
    [[ "$*" == 'up -d --no-deps --force-recreate --no-build --pull never app' ]] || exit 93
    count=0
    [[ ! -f $TEST_CASE_DIRECTORY/recreate.count ]] || read -r count < "$TEST_CASE_DIRECTORY/recreate.count"
    count=$((count + 1))
    printf '%s\n' "$count" > "$TEST_CASE_DIRECTORY/recreate.count"
    sha256sum "$TEST_DEPLOY_DIRECTORY/backend/target/crave.war" >> "$TEST_CASE_DIRECTORY/recreated-war.sha256"
    printf 'recreate-%s\n' "$count" >> "$TEST_CASE_DIRECTORY/operations.log"
    python3 "$(dirname -- "$0")/compose-fixture.py" "${compose_files[@]}" > /dev/null
    python3 - "$TEST_CASE_DIRECTORY/resolved-compose.json" "$TEST_CASE_DIRECTORY/recreated-environments.jsonl" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    config = json.load(source)
with open(sys.argv[2], "a", encoding="utf-8") as target:
    target.write(json.dumps(config["services"]["app"].get("environment", {})) + "\n")
PY
    [[ $TEST_UP_FAIL_ON != "$count" ]] || exit 17
    if [[ $TEST_INTERRUPT_ON == "$count" ]]; then
      kill -TERM "$PPID"
    fi
    ;;
  *) exit 94 ;;
esac
SH

cat > "$test_root/bin/curl" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >> "$TEST_CASE_DIRECTORY/curl.calls"
[[ "$*" == '--fail --silent --show-error --connect-timeout 2 --max-time 5 http://127.0.0.1:8080/crave/api/health' ]] || exit 95
count=0
[[ ! -f $TEST_CASE_DIRECTORY/recreate.count ]] || read -r count < "$TEST_CASE_DIRECTORY/recreate.count"
if [[ $count == 1 ]]; then
  case "$TEST_HEALTH" in
    malformed) printf '{invalid-json'; exit 0 ;;
    database-down) printf '{"success":true,"data":{"application":"UP","database":"DOWN"}}'; exit 0 ;;
    application-down) printf '{"success":true,"data":{"application":"DOWN","database":"UP"}}'; exit 0 ;;
    failure-body) printf '{"success":false,"data":{"application":"UP","database":"UP"}}'; exit 0 ;;
    http-failure) exit 22 ;;
  esac
fi
printf 'application=UP\ndatabase=UP\n'
SH

cat > "$test_root/bin/install" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail
count=0
[[ ! -f $TEST_CASE_DIRECTORY/install.count ]] || read -r count < "$TEST_CASE_DIRECTORY/install.count"
if [[ ${@: -1} != */.crave-war.* ]]; then
  "$TEST_REAL_INSTALL" "$@"
  exit
fi
count=$((count + 1))
printf '%s\n' "$count" > "$TEST_CASE_DIRECTORY/install.count"
if [[ $TEST_INSTALL_FAIL_ON == "$count" ]]; then
  # Simulate a filesystem failure after part of the staged file has been written.
  printf 'partial-file' > "${@: -1}"
  exit 19
fi
"$TEST_REAL_INSTALL" "$@"
SH

# Git Bash lacks flock. Linux uses the actual lock; the local shim checks its API.
if ! command -v flock >/dev/null; then
  cat > "$test_root/bin/flock" <<'SH'
#!/usr/bin/env bash
[[ "$*" == '-w 300 9' ]]
SH
fi
chmod +x "$test_root/bin/"*
export PATH="$test_root/bin:$PATH" TEST_REAL_INSTALL="$real_install"
passed=0

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
same_file() { cmp -s -- "$1" "$2" || fail "File changed unexpectedly: $2"; }
contains_log() { grep -Fq -- "$1" "$case_directory/deploy.log" || fail "Expected deployment log: $1"; }

new_case() {
  case_directory=$test_root/$1
  deploy_directory=$case_directory/production
  mkdir -p -- "$deploy_directory/backend/target" "$deploy_directory/database" "$case_directory/reference"
  python3 - "$deploy_directory/compose.yaml" <<'PY'
import json, sys
with open(sys.argv[1], "w", encoding="utf-8") as target:
    json.dump({"services": {
        "app": {"image": "tomcat:10.1", "environment": {
            "DB_URL": "jdbc:mysql://mysql:3306/crave", "DB_USER": "existing-database-user",
            "DB_PASSWORD": "existing-database-fixture-password", "DB_POOL_MAX_SIZE": "19",
            "PAYOS_API_KEY": "${PAYOS_API_KEY}",
        }, "volumes": [{"type": "bind", "source": "./backend/target/crave.war",
                        "target": "/usr/local/tomcat/webapps/crave.war"}]},
        "mysql": {"image": "mysql:8.4", "environment": {"MYSQL_ROOT_PASSWORD": "container-only-db-fixture"}},
        "custom-mysql": {"image": "mysql:8.4", "environment": {"MYSQL_ROOT_PASSWORD": "custom-db-fixture"}},
    }}, target)
PY
  printf 'DB_PASSWORD=existing-database-fixture-password\nPAYOS_API_KEY=obsolete-env-api-key\n' > "$deploy_directory/.env"
  printf 'existing database marker\n' > "$deploy_directory/database/data.marker"
  cp -- "$deploy_directory/compose.yaml" "$case_directory/reference/compose.yaml"
  cp -- "$deploy_directory/.env" "$case_directory/reference/.env"
  cp -- "$deploy_directory/database/data.marker" "$case_directory/reference/data.marker"
  python3 - "$case_directory/reference/old.war" "$case_directory/incoming.war" <<'PY'
import sys, zipfile
for path, version in zip(sys.argv[1:], ("old release", "new release")):
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("WEB-INF/web.xml", "<web-app />")
        archive.writestr("version.txt", version)
PY
  cp -- "$case_directory/reference/old.war" "$deploy_directory/backend/target/crave.war"
  export TEST_CASE_DIRECTORY="$case_directory" TEST_DEPLOY_DIRECTORY="$deploy_directory"
  export TEST_CONFIG=valid TEST_HEALTH=healthy TEST_UP_FAIL_ON=0 TEST_INTERRUPT_ON=0 TEST_INSTALL_FAIL_ON=0
  export TEST_MIGRATION_FAIL=0 TEST_DATABASE_SERVICE=mysql TEST_MIGRATION="$test_migration"
  export DEPLOY_PATH="$deploy_directory" COMPOSE_FILE=compose.yaml APP_SERVICE=app DB_SERVICE=mysql
  export PAYOS_CLIENT_ID=fixture-client-id PAYOS_API_KEY=fixture-api-key PAYOS_CHECKSUM_KEY=fixture-checksum-key
  export PAYOS_PUBLIC_BASE_URL=https://crave-fixture.example/crave
  export EMAIL_SMTP_HOST=smtp.gmail.com EMAIL_SMTP_PORT=587 EMAIL_SMTP_STARTTLS=true
  export EMAIL_SMTP_USERNAME=fixture-mail@example.com EMAIL_FROM=fixture-mail@example.com
  export EMAIL_SMTP_PASSWORD=fixture-mail-password EMAIL_OTP_SECRET=fixture-otp-secret-at-least-32-characters
  export TEST_EXPECTED_PUBLIC_BASE_URL=''
  unset CRAVE_STORE_ADDRESS CRAVE_STORE_LATITUDE CRAVE_STORE_LONGITUDE CRAVE_GEOCODER_URL
  export PAYOS_EXPAND=incorrect-compose-interpolation PAYOS_BAD=incorrect-compose-fallback
  export HEALTH_URL=http://127.0.0.1:8080/crave/api/health HEALTH_TIMEOUT_SECONDS=1 HEALTH_INTERVAL_SECONDS=1
  export GITHUB_WORKSPACE="$test_root/workspace"
  incoming_war=$case_directory/incoming.war
}

run_deploy() {
  local expected_status=$1 actual_status=0
  bash "$deploy_script" "$incoming_war" > "$case_directory/deploy.log" 2>&1 || actual_status=$?
  [[ $actual_status -eq $expected_status ]] || fail "Expected exit $expected_status, got $actual_status"
}

assert_protected_files() {
  same_file "$case_directory/reference/compose.yaml" "$deploy_directory/compose.yaml"
  same_file "$case_directory/reference/.env" "$deploy_directory/.env"
  same_file "$case_directory/reference/data.marker" "$deploy_directory/database/data.marker"
  local staged
  for staged in "$deploy_directory/backend/target/.crave-war."* "$deploy_directory/.deploy-backups/.crave-war."* \
    "$deploy_directory/.payos-candidate."* "$deploy_directory/.payos-activate."* "$deploy_directory/.payos-restore."* \
    "$deploy_directory/.deploy-backups/.payos-backup."* "$deploy_directory/.deploy-backups/.payos-absence."*; do
    [[ ! -e $staged ]] || fail "Staged WAR was not cleaned up: $staged"
  done
}

assert_no_secret_log() {
  local secret
  for secret in "$PAYOS_CLIENT_ID" "$PAYOS_API_KEY" "$PAYOS_CHECKSUM_KEY" "$EMAIL_SMTP_PASSWORD" "$EMAIL_OTP_SECRET" 'existing-database-fixture-password' 'container-only-db-fixture' 'previous-fixture-client' 'previous-fixture-api' 'previous-fixture-checksum' 'previous-fixture-mail-password' 'previous-fixture-otp-secret-at-least-32-characters'; do
    [[ -z $secret ]] || ! grep -Fq -- "$secret" "$case_directory/deploy.log" || fail 'Deployment output exposed fixture credentials.'
  done
}

assert_migration_before_recreation() {
  [[ -s $case_directory/migration.sql && -f $case_directory/migration.applied ]] || fail 'Additive migration was not applied.'
  cmp -s -- "$test_migration" "$case_directory/migration.sql" || fail 'Deployment did not use the pinned repository migration.'
  [[ $(head -n 1 "$case_directory/operations.log") == migration ]] || fail 'App was recreated before migration.'
}

assert_secure_file() {
  python3 - "$1" <<'PY'
import os, stat, sys
mode = stat.S_IMODE(os.stat(sys.argv[1]).st_mode)
# Windows does not represent POSIX owner/group permissions. Linux must enforce 0600.
if os.name != "nt" and mode != 0o600:
    sys.exit("Secret overlay/backup must have mode 0600.")
PY
}

make_symlink() {
  # Git Bash's default ln -s can silently copy instead of creating a link.
  # MSYS symlinks are recognized by the same Bash/realpath used by deployment.
  MSYS=winsymlinks:sys ln -s -- "$1" "$2"
  [[ -L $2 ]] || fail 'The symlink security fixture is not a symbolic link.'
}

assert_payos_overlay() {
  local overlay=$deploy_directory/.payos.compose.json
  [[ -f $overlay && ! -L $overlay ]] || fail 'App payment override is missing or unsafe.'
  assert_secure_file "$overlay"
  python3 - "$overlay" "$case_directory/resolved-compose.json" "$case_directory/reference/compose.yaml" <<'PY'
import json, os, sys
with open(sys.argv[1], encoding="utf-8") as source:
    overlay = json.load(source)
with open(sys.argv[2], encoding="utf-8") as source:
    resolved = json.load(source)
with open(sys.argv[3], encoding="utf-8") as source:
    original = json.load(source)
keys = {"PAYOS_CLIENT_ID", "PAYOS_API_KEY", "PAYOS_CHECKSUM_KEY", "PAYOS_PUBLIC_BASE_URL", "EMAIL_SMTP_HOST", "EMAIL_SMTP_PORT", "EMAIL_SMTP_USERNAME", "EMAIL_SMTP_PASSWORD", "EMAIL_FROM", "EMAIL_SMTP_STARTTLS", "EMAIL_OTP_SECRET"}
keys |= {name for name in ("CRAVE_STORE_ADDRESS", "CRAVE_STORE_LATITUDE", "CRAVE_STORE_LONGITUDE", "CRAVE_GEOCODER_URL") if os.environ.get(name, "").strip()}
assert set(overlay) == {"services"}
assert set(overlay["services"]) == {"app"}, "Override must target only the app service"
assert set(overlay["services"]["app"]) == {"environment"}, "Override must not change mounts/images"
environment = overlay["services"]["app"]["environment"]
assert set(environment) == keys, "Override must contain only payment, email, and configured delivery settings"
actual = resolved["services"]["app"]["environment"]
for key in keys:
    expected = (os.environ.get("TEST_EXPECTED_PUBLIC_BASE_URL") or os.environ[key]) if key == "PAYOS_PUBLIC_BASE_URL" else os.environ[key]
    assert actual[key] == expected, "Compose changed an incoming payment setting"
    assert environment[key] == expected.replace("$", "$$"), "Compose dollar escaping is missing"
for key, value in original["services"]["app"]["environment"].items():
    if key not in keys:
        assert actual[key] == value, "Existing database/app setting was changed"
for name in ("mysql", "custom-mysql"):
    assert resolved["services"][name] == original["services"][name], "Database service was changed"
PY
}

seed_old_overlay() {
  python3 - "$deploy_directory/.payos.compose.json" <<'PY'
import json, sys
with open(sys.argv[1], "w", encoding="utf-8") as target:
    json.dump({"services": {"app": {"environment": {
        "PAYOS_CLIENT_ID": "previous-fixture-client", "PAYOS_API_KEY": "previous-fixture-api",
        "PAYOS_CHECKSUM_KEY": "previous-fixture-checksum", "PAYOS_PUBLIC_BASE_URL": "https://previous-fixture.example/crave",
        "EMAIL_SMTP_HOST": "smtp.gmail.com", "EMAIL_SMTP_PORT": "587", "EMAIL_SMTP_STARTTLS": "true",
        "EMAIL_SMTP_USERNAME": "previous@example.com", "EMAIL_FROM": "previous@example.com",
        "EMAIL_SMTP_PASSWORD": "previous-fixture-mail-password", "EMAIL_OTP_SECRET": "previous-fixture-otp-secret-at-least-32-characters",
    }}}}, target)
PY
  chmod 600 "$deploy_directory/.payos.compose.json"
  cp -- "$deploy_directory/.payos.compose.json" "$case_directory/reference/payos.compose.json"
}

assert_old_war() { same_file "$case_directory/reference/old.war" "$deploy_directory/backend/target/crave.war"; }
assert_backup() { same_file "$case_directory/reference/old.war" "$deploy_directory/.deploy-backups/crave.war.previous"; }
assert_recreates() {
  local actual=0
  [[ ! -f $case_directory/recreate.count ]] || read -r actual < "$case_directory/recreate.count"
  [[ $actual == "$1" ]] || fail "Expected $1 app recreations, got $actual"
}
assert_recreated_wars() {
  local war
  : > "$case_directory/expected-recreated.sha256"
  for war in "$@"; do
    sha256sum "$war" | cut -d ' ' -f 1 >> "$case_directory/expected-recreated.sha256"
  done
  cut -d ' ' -f 1 "$case_directory/recreated-war.sha256" > "$case_directory/actual-recreated.sha256"
  same_file "$case_directory/expected-recreated.sha256" "$case_directory/actual-recreated.sha256"
}
pass_case() { assert_protected_files; assert_no_secret_log; passed=$((passed + 1)); printf 'PASS %s\n' "$(basename -- "$case_directory")"; }

new_case success
run_deploy 0
same_file "$incoming_war" "$deploy_directory/backend/target/crave.war"
assert_backup
assert_recreates 1
assert_recreated_wars "$incoming_war"
[[ -s $case_directory/curl.calls ]] || fail 'Successful deployment did not check application health.'
contains_log 'Deployment succeeded.'
assert_migration_before_recreation
assert_payos_overlay
pass_case

new_case configured-delivery-origin
export CRAVE_STORE_ADDRESS='Configured demo store' CRAVE_STORE_LATITUDE=10.89 CRAVE_STORE_LONGITUDE=106.76
export CRAVE_GEOCODER_URL='https://photon.komoot.io/api/'
run_deploy 0
assert_migration_before_recreation
assert_payos_overlay
pass_case

for invalid_setting in missing-longitude nonfinite-latitude invalid-geocoder; do
  new_case "delivery-$invalid_setting"
  case "$invalid_setting" in
    missing-longitude) export CRAVE_STORE_LATITUDE=10.89 ;;
    nonfinite-latitude) export CRAVE_STORE_LATITUDE=NaN CRAVE_STORE_LONGITUDE=106.76 ;;
    invalid-geocoder) export CRAVE_GEOCODER_URL='not-a-url' ;;
  esac
  run_deploy 1
  assert_old_war
  assert_recreates 0
  [[ ! -e $case_directory/migration.applied ]] || fail 'Invalid delivery configuration reached the migration.'
  pass_case
done

for health in malformed database-down application-down failure-body http-failure; do
  new_case "rollback-$health"
  export TEST_HEALTH="$health"
  run_deploy 1
  assert_old_war
  assert_backup
  assert_recreates 2
  assert_recreated_wars "$incoming_war" "$case_directory/reference/old.war"
  contains_log 'Rollback completed; the previous application is healthy.'
  [[ ! -e $deploy_directory/.payos.compose.json ]] || fail 'Rollback retained a first-install payment override.'
  assert_migration_before_recreation
  pass_case
done

new_case recreate-failure
export TEST_UP_FAIL_ON=1
run_deploy 17
assert_old_war
assert_backup
assert_recreates 2
assert_recreated_wars "$incoming_war" "$case_directory/reference/old.war"
contains_log 'Rollback completed; the previous application is healthy.'
pass_case

new_case interrupted-recreation
export TEST_INTERRUPT_ON=1
run_deploy 143
assert_old_war
assert_backup
assert_recreates 2
assert_recreated_wars "$incoming_war" "$case_directory/reference/old.war"
contains_log 'Rollback completed; the previous application is healthy.'
pass_case

new_case invalid-artifact
printf 'not a WAR archive\n' > "$incoming_war"
run_deploy 1
assert_old_war
assert_recreates 0
[[ ! -e $case_directory/docker.calls ]] || fail 'Invalid artifact reached Docker.'
pass_case

for config in missing-bind missing-service; do
  new_case "preflight-$config"
  export TEST_CONFIG="$config"
  run_deploy 1
  assert_old_war
  assert_recreates 0
  [[ ! -e $deploy_directory/.deploy-backups/crave.war.previous ]] || fail 'Preflight failure created a WAR backup.'
  pass_case
done

new_case backup-staging-failure
export TEST_INSTALL_FAIL_ON=1
run_deploy 1
assert_old_war
assert_recreates 0
[[ ! -e $deploy_directory/.deploy-backups/crave.war.previous ]] || fail 'Failed backup replaced the backup WAR.'
pass_case

new_case new-war-staging-failure
export TEST_INSTALL_FAIL_ON=2
run_deploy 1
assert_old_war
assert_backup
assert_recreates 1
assert_recreated_wars "$case_directory/reference/old.war"
contains_log 'Rollback completed; the previous application is healthy.'
pass_case

new_case rollback-staging-failure
export TEST_HEALTH=database-down TEST_INSTALL_FAIL_ON=3
run_deploy 1
same_file "$incoming_war" "$deploy_directory/backend/target/crave.war"
assert_backup
assert_recreates 1
assert_recreated_wars "$incoming_war"
contains_log 'Rollback did not become healthy.'
pass_case

new_case same-artifact-and-deployed-war
incoming_war=$deploy_directory/backend/target/crave.war
run_deploy 1
assert_old_war
assert_recreates 0
pass_case

new_case production-in-runner-workspace
export GITHUB_WORKSPACE="$deploy_directory"
run_deploy 1
assert_old_war
assert_recreates 0
pass_case

new_case compose-path-escape
printf 'outside compose configuration\n' > "$case_directory/outside-compose.yaml"
export COMPOSE_FILE=../outside-compose.yaml
run_deploy 1
assert_old_war
assert_recreates 0
[[ $(cat "$case_directory/outside-compose.yaml") == 'outside compose configuration' ]] || fail 'Outside Compose file changed.'
pass_case

new_case unsafe-health-url
export HEALTH_URL=http://example.invalid:8080/crave/api/health
run_deploy 1
assert_old_war
assert_recreates 0
pass_case

new_case backup-destination-directory
mkdir -p -- "$deploy_directory/.deploy-backups/crave.war.previous"
printf 'existing backup directory contents\n' > "$deploy_directory/.deploy-backups/crave.war.previous/marker"
run_deploy 1
assert_old_war
assert_recreates 0
[[ $(cat "$deploy_directory/.deploy-backups/crave.war.previous/marker") == 'existing backup directory contents' ]] || fail 'Backup directory contents changed.'
[[ ! -e $deploy_directory/.deploy-backups/crave.war.previous/crave.war ]] || fail 'Backup was copied into a directory.'
pass_case

for missing in PAYOS_CLIENT_ID PAYOS_API_KEY PAYOS_CHECKSUM_KEY PAYOS_PUBLIC_BASE_URL EMAIL_SMTP_USERNAME EMAIL_SMTP_PASSWORD EMAIL_FROM EMAIL_OTP_SECRET; do
  new_case "missing-$missing"
  export "$missing="
  run_deploy 1
  assert_old_war
  assert_recreates 0
  [[ ! -e $case_directory/migration.sql && ! -e $case_directory/recreate.count ]] || fail 'Missing payment configuration reached a mutating Docker command.'
  [[ ! -e $deploy_directory/.payos.compose.json ]] || fail 'Missing configuration wrote a payment override.'
  pass_case
done

for invalid in plaintext weak-secret invalid-port invalid-email; do
  new_case "invalid-email-$invalid"
  case "$invalid" in
    plaintext) export EMAIL_SMTP_STARTTLS=false ;;
    weak-secret) export EMAIL_OTP_SECRET=short ;;
    invalid-port) export EMAIL_SMTP_PORT=465 ;;
    invalid-email) export EMAIL_FROM='not-an-email' ;;
  esac
  run_deploy 1
  assert_old_war
  assert_recreates 0
  [[ ! -e $case_directory/migration.sql ]] || fail 'Invalid email configuration reached migration.'
  pass_case
done

new_case migration-failure
export TEST_MIGRATION_FAIL=1
run_deploy 31
assert_old_war
assert_recreates 0
[[ -s $case_directory/migration.sql ]] || fail 'Migration failure fixture did not reach the database command.'
[[ ! -e $case_directory/migration.applied ]] || fail 'Failed migration was marked successful.'
[[ ! -e $deploy_directory/.payos.compose.json ]] || fail 'Failed migration activated new payment settings.'
pass_case

new_case env-only-payos-preserves-db-settings
export PAYOS_CLIENT_ID=fixture-replacement-client PAYOS_API_KEY=fixture-replacement-api PAYOS_CHECKSUM_KEY=fixture-replacement-checksum
export PAYOS_PUBLIC_BASE_URL=http://payments-fixture.example:8080/crave
run_deploy 0
assert_backup
assert_recreates 1
assert_migration_before_recreation
assert_payos_overlay
[[ -f $deploy_directory/.deploy-backups/payos.compose.previous.absent ]] || fail 'First-install absence was not backed up.'
assert_secure_file "$deploy_directory/.deploy-backups/payos.compose.previous.absent"
pass_case

new_case payment-dollar-escaping
export PAYOS_CLIENT_ID='fixture-client-$PAYOS_EXPAND'
export PAYOS_API_KEY='fixture-api-$$-literal-${PAYOS_BAD:-fallback}'
export PAYOS_CHECKSUM_KEY='fixture-checksum-"quoted"\slash&$PAYOS_EXPAND'
export EMAIL_SMTP_PASSWORD='fixture-mail-"quoted"\slash&$PAYOS_EXPAND'
export EMAIL_OTP_SECRET='fixture-otp-secret-at-least-32-characters-$PAYOS_EXPAND'
run_deploy 0
assert_payos_overlay
assert_migration_before_recreation
python3 - "$case_directory/serialized-compose.json" "$case_directory/recreated-environments.jsonl" <<'PY'
import json, os, sys
with open(sys.argv[1], encoding="utf-8") as source:
    serialized = json.load(source)
with open(sys.argv[2], encoding="utf-8") as source:
    runtime = [json.loads(line) for line in source]
assert serialized["services"]["app"]["environment"]["PAYOS_API_KEY"] == os.environ["PAYOS_API_KEY"].replace("$", "$$")
assert runtime[0]["PAYOS_API_KEY"] == os.environ["PAYOS_API_KEY"], "Serialization escaping changed the runtime credential"
assert runtime[0]["EMAIL_SMTP_PASSWORD"] == os.environ["EMAIL_SMTP_PASSWORD"], "Serialization changed the SMTP password"
assert runtime[0]["EMAIL_OTP_SECRET"] == os.environ["EMAIL_OTP_SECRET"], "Serialization changed the OTP secret"
PY
pass_case

new_case unescaped-compose-serialization-is-rejected
export TEST_CONFIG=raw-dollar-json KEPT_LITERAL=incorrect-compose-interpolation
export PAYOS_CLIENT_ID='fixture-client-$PAYOS_EXPAND'
export PAYOS_API_KEY='fixture$$api${KEPT_LITERAL}'
export PAYOS_CHECKSUM_KEY='fixture-checksum-$PAYOS_EXPAND'
run_deploy 1
assert_old_war
assert_recreates 0
[[ ! -e $case_directory/migration.sql ]] || fail 'Noncanonical Compose serialization reached migration.'
[[ ! -e $deploy_directory/.payos.compose.json ]] || fail 'Noncanonical Compose serialization activated payment settings.'
python3 - "$case_directory/serialized-compose.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    serialized = json.load(source)
assert serialized["services"]["app"]["environment"]["PAYOS_API_KEY"] == "fixture$$api${KEPT_LITERAL}"
PY
pass_case

new_case rollback-restores-previous-payment-overlay
seed_old_overlay
export TEST_HEALTH=database-down
run_deploy 1
assert_old_war
assert_recreates 2
same_file "$case_directory/reference/payos.compose.json" "$deploy_directory/.payos.compose.json"
same_file "$case_directory/reference/payos.compose.json" "$deploy_directory/.deploy-backups/payos.compose.previous.json"
assert_secure_file "$deploy_directory/.payos.compose.json"
assert_secure_file "$deploy_directory/.deploy-backups/payos.compose.previous.json"
[[ ! -e $deploy_directory/.deploy-backups/payos.compose.previous.absent ]] || fail 'Previous overlay was backed up as absent.'
python3 - "$case_directory/recreated-environments.jsonl" <<'PY'
import json, os, sys
with open(sys.argv[1], encoding="utf-8") as source:
    attempts = [json.loads(line) for line in source]
assert len(attempts) == 2
assert attempts[0]["PAYOS_API_KEY"] == os.environ["PAYOS_API_KEY"]
assert attempts[1]["PAYOS_API_KEY"] == "previous-fixture-api", "Rollback recreated with new credentials"
assert attempts[1]["PAYOS_PUBLIC_BASE_URL"] == "https://previous-fixture.example/crave"
assert attempts[1]["EMAIL_SMTP_PASSWORD"] == "previous-fixture-mail-password", "Rollback retained the new SMTP password"
assert attempts[1]["EMAIL_OTP_SECRET"] == "previous-fixture-otp-secret-at-least-32-characters", "Rollback retained the new OTP secret"
PY
assert_migration_before_recreation
pass_case

new_case rollback-restores-payment-overlay-absence
export TEST_UP_FAIL_ON=1
run_deploy 17
assert_old_war
assert_recreates 2
[[ ! -e $deploy_directory/.payos.compose.json ]] || fail 'First-install override survived rollback.'
[[ -f $deploy_directory/.deploy-backups/payos.compose.previous.absent ]] || fail 'First-install absence backup is missing.'
python3 - "$case_directory/recreated-environments.jsonl" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    attempts = [json.loads(line) for line in source]
assert len(attempts) == 2
assert attempts[1]["PAYOS_API_KEY"] == "obsolete-env-api-key", "Workflow secrets changed base .env interpolation"
assert "PAYOS_CLIENT_ID" not in attempts[1], "Rollback retained the first overlay environment"
PY
assert_migration_before_recreation
pass_case

new_case backup-directory-symlink
mkdir -p -- "$case_directory/outside-backups"
printf 'outside backup marker\n' > "$case_directory/outside-backups/marker"
make_symlink "$case_directory/outside-backups" "$deploy_directory/.deploy-backups"
run_deploy 1
assert_old_war
assert_recreates 0
[[ $(cat "$case_directory/outside-backups/marker") == 'outside backup marker' ]] || fail 'Outside backup contents changed.'
[[ ! -e $case_directory/outside-backups/crave.war.previous && ! -e $case_directory/outside-backups/payos.compose.previous.json ]] || fail 'Backup symlink allowed an outside write.'
pass_case

new_case payment-backup-symlink
seed_old_overlay
mkdir -p -- "$deploy_directory/.deploy-backups"
printf 'outside payment backup marker\n' > "$case_directory/outside-payment-backup.json"
make_symlink "$case_directory/outside-payment-backup.json" "$deploy_directory/.deploy-backups/payos.compose.previous.json"
run_deploy 1
assert_old_war
assert_recreates 0
same_file "$case_directory/reference/payos.compose.json" "$deploy_directory/.payos.compose.json"
[[ $(cat "$case_directory/outside-payment-backup.json") == 'outside payment backup marker' ]] || fail 'Payment backup followed a symlink.'
pass_case

new_case payment-overlay-destination-directory
mkdir -p -- "$deploy_directory/.payos.compose.json"
printf 'existing overlay directory marker\n' > "$deploy_directory/.payos.compose.json/marker"
run_deploy 1
assert_old_war
assert_recreates 0
[[ $(cat "$deploy_directory/.payos.compose.json/marker") == 'existing overlay directory marker' ]] || fail 'Payment overlay directory contents changed.'
[[ ! -e $deploy_directory/.payos.compose.json/.payos-candidate ]] || fail 'Payment override was copied into a directory.'
pass_case

new_case payment-overlay-destination-symlink
printf 'outside payment settings marker\n' > "$case_directory/outside-payment-settings.json"
make_symlink "$case_directory/outside-payment-settings.json" "$deploy_directory/.payos.compose.json"
run_deploy 1
assert_old_war
assert_recreates 0
[[ $(cat "$case_directory/outside-payment-settings.json") == 'outside payment settings marker' ]] || fail 'Payment overlay followed a symlink.'
pass_case

invalid_base_urls=(
  'ftp://crave-fixture.example/crave'
  'https://user:password@crave-fixture.example/crave'
  'https://crave-fixture.example/crave?debug=1'
  'https://crave-fixture.example/crave#fragment'
  'https:///crave'
  'http://localhost:8080/crave'
  'https://127.0.0.1/crave'
  'https://192.168.1.10/crave'
  'https://crave-fixture.local/crave'
  'https://crave-fixture.example/another-application'
  'https://@crave-fixture.example/crave'
  'https://crave-fixture.example/crave?'
  'https://crave-fixture.example/crave#'
  'https://crave-fixture.example/crave//'
  'https://127.1/crave'
  'https://0x7f000001/crave'
  'https://crave-fixture.example:99999/crave'
)
for index in "${!invalid_base_urls[@]}"; do
  new_case "invalid-public-base-url-$index"
  export PAYOS_PUBLIC_BASE_URL="${invalid_base_urls[$index]}"
  run_deploy 1
  assert_old_war
  assert_recreates 0
  [[ ! -e $case_directory/migration.sql && ! -e $case_directory/recreate.count ]] || fail 'Invalid public URL reached a mutating Docker command.'
  pass_case
done

new_case public-base-url-requires-application-context
export PAYOS_PUBLIC_BASE_URL=https://payments-fixture.example
run_deploy 1
assert_old_war
assert_recreates 0
[[ ! -e $case_directory/migration.sql ]] || fail 'Missing application context reached migration.'
pass_case

new_case public-base-url-trailing-slash-normalization
export PAYOS_PUBLIC_BASE_URL=http://payments-fixture.example:8080/crave/
export TEST_EXPECTED_PUBLIC_BASE_URL=http://payments-fixture.example:8080/crave
run_deploy 0
assert_payos_overlay
assert_migration_before_recreation
pass_case

new_case custom-database-service
export DB_SERVICE=custom-mysql TEST_DATABASE_SERVICE=custom-mysql
run_deploy 0
assert_payos_overlay
assert_migration_before_recreation
assert_recreates 1
grep -Fq 'exec -T custom-mysql sh -c' "$case_directory/docker.calls" || fail 'Custom database service was ignored.'
pass_case

new_case secret-compose-diagnostic-is-suppressed
export TEST_CONFIG=secret-stderr-failure
run_deploy 1
assert_old_war
assert_recreates 0
[[ ! -e $case_directory/migration.sql ]] || fail 'Invalid Compose configuration reached migration.'
pass_case

printf 'Deployment regression tests passed: %s scenarios.\n' "$passed"
