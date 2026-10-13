#!/usr/bin/env bash
# Exercise deployment and rollback with isolated fixtures; never call real Docker.
set -Eeuo pipefail

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
deploy_script=$(realpath -- "$script_directory/../../scripts/deploy-ec2.sh")
for dependency in bash python3 cmp mktemp install sha256sum unzip; do
  command -v "$dependency" >/dev/null || { printf 'Missing test dependency: %s\n' "$dependency" >&2; exit 1; }
done
real_install=$(command -v install)
temp_directory=$(realpath -- "${TMPDIR:-/tmp}")
test_root=$(mktemp -d "$temp_directory/crave-deploy-test.XXXXXX")
[[ $test_root == "$temp_directory/"crave-deploy-test.* ]] || exit 1
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
shift 2
case "$1" in
  version) printf 'Docker Compose fixture\n' ;;
  config)
    python3 - "$TEST_DEPLOY_DIRECTORY/backend/target/crave.war" "$TEST_CONFIG" <<'PY'
import json, os, sys
source = os.path.realpath(sys.argv[1])
mode = sys.argv[2]
volumes = [] if mode == "missing-bind" else [{
    "type": "bind", "source": source,
    "target": "/usr/local/tomcat/webapps/crave.war",
}]
service = "different-app" if mode == "missing-service" else "app"
print(json.dumps({"services": {service: {"volumes": volumes}, "mysql": {"image": "mysql:8.4"}}}))
PY
    ;;
  up)
    [[ "$*" == 'up -d --no-deps --force-recreate --no-build --pull never app' ]] || exit 93
    count=0
    [[ ! -f $TEST_CASE_DIRECTORY/recreate.count ]] || read -r count < "$TEST_CASE_DIRECTORY/recreate.count"
    count=$((count + 1))
    printf '%s\n' "$count" > "$TEST_CASE_DIRECTORY/recreate.count"
    sha256sum "$TEST_DEPLOY_DIRECTORY/backend/target/crave.war" >> "$TEST_CASE_DIRECTORY/recreated-war.sha256"
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
printf '{"success":true,"data":{"application":"UP","database":"UP"},"error":null}'
SH

cat > "$test_root/bin/install" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail
count=0
[[ ! -f $TEST_CASE_DIRECTORY/install.count ]] || read -r count < "$TEST_CASE_DIRECTORY/install.count"
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
  printf 'fixture compose configuration\n' > "$deploy_directory/compose.yaml"
  printf 'fixture database environment\n' > "$deploy_directory/.env"
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
  export DEPLOY_PATH="$deploy_directory" COMPOSE_FILE=compose.yaml APP_SERVICE=app
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
  for staged in "$deploy_directory/backend/target/.crave-war."* "$deploy_directory/.deploy-backups/.crave-war."*; do
    [[ ! -e $staged ]] || fail "Staged WAR was not cleaned up: $staged"
  done
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
pass_case() { assert_protected_files; passed=$((passed + 1)); printf 'PASS %s\n' "$(basename -- "$case_directory")"; }

new_case success
run_deploy 0
same_file "$incoming_war" "$deploy_directory/backend/target/crave.war"
assert_backup
assert_recreates 1
assert_recreated_wars "$incoming_war"
[[ -s $case_directory/curl.calls ]] || fail 'Successful deployment did not check application health.'
contains_log 'Deployment succeeded.'
pass_case

for health in malformed database-down application-down failure-body http-failure; do
  new_case "rollback-$health"
  export TEST_HEALTH="$health"
  run_deploy 1
  assert_old_war
  assert_backup
  assert_recreates 2
  assert_recreated_wars "$incoming_war" "$case_directory/reference/old.war"
  contains_log 'Rollback completed; the previous application is healthy.'
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
  [[ ! -d $deploy_directory/.deploy-backups ]] || fail 'Preflight failure created a WAR backup.'
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

printf 'Deployment regression tests passed: %s scenarios.\n' "$passed"
