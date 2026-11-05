#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_DIR"
compose=(docker compose "$@")

# Let Compose resolve shell/.env/--env-file precedence without executing env files.
"${compose[@]}" config --quiet
startup_timeout=$("${compose[@]}" config --environment | sed -n 's/^STARTUP_TIMEOUT_SECONDS=//p')
startup_timeout=${startup_timeout:-180}
if [[ ! "$startup_timeout" =~ ^[1-9][0-9]*$ ]]; then
    echo "STARTUP_TIMEOUT_SECONDS must be a positive integer" >&2
    exit 1
fi
key_directory=$("${compose[@]}" config --environment | sed -n 's/^JWT_KEY_DIR=//p')
key_directory=${key_directory:-$PROJECT_DIR/.local/jwt}
if [[ "$key_directory" != /* ]]; then key_directory="$PROJECT_DIR/$key_directory"; fi
export JWT_KEY_DIR="$key_directory"
bash "$SCRIPT_DIR/generate-jwt-keys.sh"

"${compose[@]}" up --build -d
# One-shot kafka-init must complete successfully before waiting on long-lived services.
init_container=$("${compose[@]}" ps -aq kafka-init)
init_deadline=$((SECONDS + startup_timeout))
while true; do
    init_state=$(docker inspect --format '{{.State.Status}} {{.State.ExitCode}}' "$init_container")
    if [[ "$init_state" == "exited 0" ]]; then break; fi
    if [[ "$init_state" == exited* || "$init_state" == dead* ]] || (( SECONDS >= init_deadline )); then
        "${compose[@]}" logs kafka-init
        echo "Kafka topic initialization failed or timed out: $init_state" >&2
        exit 1
    fi
    sleep 1
done
"${compose[@]}" up -d --wait --wait-timeout "$startup_timeout" \
    api-gateway pgadmin redisinsight kafka-1 kafka-2 kafka-3

# Discover the actual published port, including random ports from Compose overrides.
gateway_address=$("${compose[@]}" port api-gateway 8080)
gateway_address=${gateway_address%%$'\n'*}
gateway_port=${gateway_address##*:}
if [[ ! "$gateway_port" =~ ^[0-9]+$ ]]; then
    echo "Gateway must publish port 8080 to run the HTTP startup checks" >&2
    exit 1
fi
case "$gateway_address" in
    0.0.0.0:*) gateway_url="http://127.0.0.1:$gateway_port" ;;
    \[::\]:*) gateway_url="http://[::1]:$gateway_port" ;;
    *) gateway_url="http://$gateway_address" ;;
esac
http_deadline=$((SECONDS + startup_timeout))
for path in /swagger-ui/index.html /v3/api-docs/swagger-config \
    /v3/api-docs/user-service /v3/api-docs/tracker-service \
    /v3/api-docs/notification-service /v3/api-docs/file-service; do
    until curl --fail --silent --location --max-time 5 --output /dev/null "$gateway_url$path"; do
        if (( SECONDS >= http_deadline )); then
            echo "HTTP startup check timed out: $path" >&2
            exit 1
        fi
        sleep 1
    done
    echo "HTTP is ready: $path"
done
"${compose[@]}" ps
echo "Swagger: $gateway_url/swagger-ui/index.html"
