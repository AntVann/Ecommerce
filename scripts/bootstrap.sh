#!/usr/bin/env sh
set -eu

REPOSITORY_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPOSITORY_ROOT"

command -v java >/dev/null 2>&1 || { echo "Java 21 is required." >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "Docker is required." >&2; exit 1; }

java -version 2>&1 | head -n 1 | grep -q 'version "21\.' || {
  echo "Java 21 is required." >&2
  exit 1
}

docker compose version

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from non-secret local development examples."
else
  echo "Preserved existing .env."
fi

chmod +x mvnw scripts/*.sh
./mvnw --version
echo "MarketFlow foundation prerequisites are ready."
