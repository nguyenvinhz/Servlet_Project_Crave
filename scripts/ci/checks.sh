#!/usr/bin/env bash
# Fast checks shared by local development and GitHub Actions.
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
for tool in actionlint shellcheck node; do
  command -v "$tool" >/dev/null || { printf 'Missing check tool: %s\n' "$tool" >&2; exit 1; }
done
actionlint
shellcheck --external-sources scripts/deploy-ec2.sh scripts/tests/deploy-ec2-test.sh scripts/ci/*.sh
find frontend/assets/js frontend/tests -type f \( -name '*.js' -o -name '*.cjs' \) \
  -print0 | xargs -0 -r -n 1 node --check
