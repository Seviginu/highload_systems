#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

export POSTGRES_PORT="${POSTGRES_PORT:-55432}"
export REDIS_PORT="${REDIS_PORT:-56379}"
export SERVER_PORT="${SERVER_PORT:-58080}"
export GATEWAY_PORT="${GATEWAY_PORT:-58081}"
export USER_SERVICE_PORT="${USER_SERVICE_PORT:-58082}"
export CONFIG_SERVER_PORT="${CONFIG_SERVER_PORT:-58888}"
export EUREKA_SERVER_PORT="${EUREKA_SERVER_PORT:-58761}"

cd "${PROJECT_DIR}"

bash "${SCRIPT_DIR}/generate-jwt-keys.sh"
docker compose up --build -d --wait --wait-timeout "${STARTUP_TIMEOUT_SECONDS:-180}"
swagger_deadline=$((SECONDS + ${STARTUP_TIMEOUT_SECONDS:-180}))
for service in user-service tracker-service; do
    until curl --fail --silent --max-time 5 --output /dev/null \
            "http://localhost:${GATEWAY_PORT}/v3/api-docs/${service}"; do
        if (( SECONDS >= swagger_deadline )); then
            echo "OpenAPI route did not become ready: ${service}" >&2
            exit 1
        fi
        sleep 1
    done
    echo "OpenAPI is ready: ${service}"
done
docker compose ps
