#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

export POSTGRES_PORT="${POSTGRES_PORT:-55432}"
export REDIS_PORT="${REDIS_PORT:-56379}"
export ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-59200}"
export LOGSTASH_MONITORING_PORT="${LOGSTASH_MONITORING_PORT:-59600}"
export KIBANA_PORT="${KIBANA_PORT:-55601}"
export SERVER_PORT="${SERVER_PORT:-58080}"

cd "${PROJECT_DIR}"

echo "Starting VS Lab 1:"
echo "  application:   http://localhost:${SERVER_PORT}"
echo "  PostgreSQL:    localhost:${POSTGRES_PORT}"
echo "  Redis:         localhost:${REDIS_PORT}"
echo "  Elasticsearch: http://localhost:${ELASTICSEARCH_PORT}"
echo "  Logstash API:  http://localhost:${LOGSTASH_MONITORING_PORT}"
echo "  Kibana:        http://localhost:${KIBANA_PORT}"

docker compose up --build -d
docker compose ps
