#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

# Separate project, fresh volumes and automatically assigned host ports.
export SMOKE_PROJECT="vs-lab2-config-smoke-$$"
export POSTGRES_PORT=0 REDIS_PORT=0 SERVER_PORT=0
export CONFIG_SERVER_PORT=0 EUREKA_SERVER_PORT=0

cleanup() {
    result=$?
    if [ "${result}" -ne 0 ]; then
        docker compose -p "${SMOKE_PROJECT}" logs --tail=80 config-server discovery-server app
    fi
    docker compose -p "${SMOKE_PROJECT}" down --volumes --remove-orphans
    exit "${result}"
}
trap cleanup EXIT

docker compose -p "${SMOKE_PROJECT}" up --build -d --wait --wait-timeout 180

python3 - <<'PY'
import json
import os
import subprocess
import time
import urllib.error
import urllib.request

project = os.environ['SMOKE_PROJECT']


def base_url(service, port):
    address = subprocess.check_output(
        ['docker', 'compose', '-p', project, 'port', service, str(port)], text=True
    ).strip().splitlines()[0]
    return f'http://127.0.0.1:{address.rsplit(":", 1)[1]}'


def get_json(url):
    request = urllib.request.Request(url, headers={'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


config = base_url('config-server', 8888)
discovery = base_url('discovery-server', 8761)
tracker = base_url('app', 8080)

for name in ('tracker-service', 'discovery-server'):
    environment = get_json(f'{config}/{name}/default')
    sources = environment['propertySources']
    assert environment['name'] == name
    assert any(f'{name}.yml' in source['name'] for source in sources), sources
    assert any(source['source'].get('info.app.configuration-source') == 'config-server'
               for source in sources), sources
    print(f'Config Server serves shared and service configuration: {name}')

for name, base in (('tracker-service', tracker), ('discovery-server', discovery)):
    info = get_json(f'{base}/actuator/info')
    assert info['app']['configuration-source'] == 'config-server', info
    print(f'{name} loaded remote configuration')

deadline = time.monotonic() + 90
while True:
    try:
        for name in ('TRACKER-SERVICE', 'CONFIG-SERVER'):
            application = get_json(f'{discovery}/eureka/apps/{name}')['application']
            instances = application['instance']
            if isinstance(instances, dict):
                instances = [instances]
            assert any(instance['status'] == 'UP' for instance in instances)
        break
    except (urllib.error.URLError, AssertionError, KeyError):
        if time.monotonic() >= deadline:
            raise
        time.sleep(2)
print('Eureka contains UP instances of tracker-service and config-server')

request = urllib.request.Request(f'{tracker}/api/v1/users?page=0&size=20')
with urllib.request.urlopen(request, timeout=5) as response:
    assert response.status == 200
    assert response.headers['X-Total-Count'] == '0'
    assert json.load(response) == []
print('Tracker HTTP API and database are available; smoke checks passed')
PY
