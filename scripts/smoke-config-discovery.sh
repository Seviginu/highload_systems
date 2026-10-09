#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

# Separate project, fresh volumes and automatically assigned host ports.
export SMOKE_PROJECT="vs-lab2-config-smoke-$$"
export POSTGRES_PORT=0 REDIS_PORT=0 SERVER_PORT=0
export CONFIG_SERVER_PORT=0 EUREKA_SERVER_PORT=0
export GATEWAY_PORT=0

cleanup() {
    result=$?
    if [ "${result}" -ne 0 ]; then
        docker compose -p "${SMOKE_PROJECT}" logs --tail=80 config-server discovery-server app api-gateway
    fi
    docker compose -p "${SMOKE_PROJECT}" down --volumes --remove-orphans
    exit "${result}"
}
trap cleanup EXIT

docker compose -p "${SMOKE_PROJECT}" up --build -d --wait --wait-timeout 180

python3 -u - <<'PY'
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.request
from urllib.parse import urljoin, urlparse

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
gateway = base_url('api-gateway', 8080)

for name in ('tracker-service', 'discovery-server', 'api-gateway'):
    environment = get_json(f'{config}/{name}/default')
    sources = environment['propertySources']
    assert environment['name'] == name
    assert any(f'{name}.yml' in source['name'] for source in sources), sources
    assert any(source['source'].get('info.app.configuration-source') == 'config-server'
               for source in sources), sources
    print(f'Config Server serves shared and service configuration: {name}')

for name, base in (('tracker-service', tracker), ('discovery-server', discovery), ('api-gateway', gateway)):
    info = get_json(f'{base}/actuator/info')
    assert info['app']['configuration-source'] == 'config-server', info
    print(f'{name} loaded remote configuration')

deadline = time.monotonic() + 90
while True:
    try:
        for name in ('TRACKER-SERVICE', 'CONFIG-SERVER', 'API-GATEWAY'):
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
print('Eureka contains UP instances of tracker-service, config-server and api-gateway')

request = urllib.request.Request(f'{tracker}/api/v1/users?page=0&size=20')
with urllib.request.urlopen(request, timeout=5) as response:
    assert response.status == 200
    assert response.headers['X-Total-Count'] == '0'
    assert json.load(response) == []
print('Tracker HTTP API and database are available')

# Eureka registration and the Gateway registry cache are eventually consistent.
deadline = time.monotonic() + 90
while True:
    try:
        assert get_json(f'{gateway}/api/v1/users?page=0&size=20') == []
        break
    except urllib.error.HTTPError as error:
        if error.code != 503 or time.monotonic() >= deadline:
            raise
        time.sleep(2)

swagger_config = get_json(f'{gateway}/v3/api-docs/swagger-config')
assert swagger_config['urls'] == [
    {'name': 'tracker-service', 'url': '/v3/api-docs/tracker-service'}
], swagger_config
spec = get_json(urljoin(gateway, swagger_config['urls'][0]['url']))
assert spec['servers'] == [{'url': '/'}], spec.get('servers')
assert all(path in spec['paths'] for path in (
    '/api/v1/users', '/api/v1/projects', '/api/v1/projects/{projectId}/members',
    '/api/v1/tasks', '/api/v1/tasks/feed', '/api/v1/tasks/{id}/move', '/api/v1/labels'
))

with urllib.request.urlopen(f'{gateway}/swagger-ui/index.html', timeout=5) as response:
    ui_url = response.url
    html = response.read().decode()
    assert response.status == 200 and 'swagger-ui' in html
assets = [value for value in re.findall(r'(?:src|href)="([^"]+)"', html)
          if value.endswith(('.js', '.css'))]
assert any('swagger-ui-bundle' in asset for asset in assets), assets
for asset in assets:
    with urllib.request.urlopen(urljoin(ui_url, asset), timeout=5) as response:
        assert response.status == 200 and response.read()
print('Gateway serves Swagger UI, its assets and the tracker OpenAPI specification')

# Use the server URL from OpenAPI, as Swagger Try it out does.
api_base = urljoin(gateway, spec['servers'][0]['url']).rstrip('/')
assert api_base == gateway


def api_request(path, method='GET', data=None, expected=200):
    body = None if data is None else json.dumps(data).encode()
    request = urllib.request.Request(api_base + path, data=body, method=method,
                                     headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(request, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        payload = response.read()
        assert response.status == expected, (path, response.status, payload)
        return (json.loads(payload) if payload else None), response.headers


lead, headers = api_request('/api/v1/users', 'POST', {
    'name': 'Smoke Team Lead', 'email': 'lead@example.com', 'role': 'TEAM_LEAD'
}, expected=201)
location = headers['Location']
assert urlparse(location).netloc == urlparse(gateway).netloc, location
assert get_json(location)['id'] == lead['id']

project, _ = api_request('/api/v1/projects', 'POST', {
    'name': 'Smoke Project', 'code': 'SMOKE', 'status': 'ACTIVE', 'teamLeadId': lead['id']
}, expected=201)
label, _ = api_request('/api/v1/labels', 'POST', {
    'name': 'smoke', 'color': '#123456'
}, expected=201)
task, headers = api_request('/api/v1/tasks', 'POST', {
    'taskKey': 'SMOKE-1', 'title': 'Gateway task', 'status': 'TODO', 'priority': 'MEDIUM',
    'authorId': lead['id'], 'assigneeId': lead['id'], 'projectId': project['id'],
    'labelIds': [label['id']]
}, expected=201)
assert urlparse(headers['Location']).netloc == urlparse(gateway).netloc, headers['Location']

members, headers = api_request(f'/api/v1/projects/{project["id"]}/members?page=0&size=20')
assert headers['X-Total-Count'] == '1' and members[0]['userId'] == lead['id']
tasks, headers = api_request(f'/api/v1/tasks?projectId={project["id"]}&size=20')
assert headers['X-Total-Count'] == '1' and tasks[0]['id'] == task['id']
feed, headers = api_request('/api/v1/tasks/feed?limit=20')
assert 'X-Total-Count' not in headers and str(task['id']) in json.dumps(feed)

validation_error, _ = api_request('/api/v1/users?size=51', expected=400)
assert validation_error['status'] == 400 and validation_error['message']
missing_error, _ = api_request('/api/v1/tasks/999999', expected=404)
assert missing_error['status'] == 404 and missing_error['message']

_, headers = api_request(f'/api/v1/tasks/{task["id"]}', 'DELETE', expected=204)
api_request(f'/api/v1/tasks/{task["id"]}', expected=404)
print('Gateway forwards CRUD, nested routes, pagination, feed, Location and structured errors')
print('Gateway and common Swagger smoke checks passed')
PY
