#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

export POSTGRES_PORT="${POSTGRES_PORT:-55432}"
export REDIS_PORT="${REDIS_PORT:-56379}"
export SERVER_PORT="${SERVER_PORT:-58080}"
export CONFIG_SERVER_PORT="${CONFIG_SERVER_PORT:-58888}"
export EUREKA_SERVER_PORT="${EUREKA_SERVER_PORT:-58761}"

cd "${PROJECT_DIR}"

docker compose up --build -d
docker compose ps
