#!/usr/bin/env sh
set -eu

REPOSITORY_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CONTRACTS_PATH="$REPOSITORY_ROOT/contracts"
NODE_IMAGE="node:22.20.0-alpine"

if command -v node >/dev/null 2>&1 && [ "$(node --version | sed 's/^v//' | cut -d. -f1)" -ge 20 ]; then
  cd "$CONTRACTS_PATH"
  npm ci --ignore-scripts
  npm run lint
  exit 0
fi

command -v docker >/dev/null 2>&1 || {
  echo "Contract validation requires Node.js 20+ or Docker." >&2
  exit 1
}

docker run --rm \
  --volume "$CONTRACTS_PATH:/contracts" \
  --workdir /contracts \
  "$NODE_IMAGE" \
  sh -c 'npm ci --ignore-scripts && npm run lint'

