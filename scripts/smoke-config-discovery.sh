#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

# Separate project, fresh volumes and automatically assigned host ports.
export SMOKE_PROJECT="vs-lab2-config-smoke-$$"
export POSTGRES_PORT=0 REDIS_PORT=0 SERVER_PORT=0
export CONFIG_SERVER_PORT=0 EUREKA_SERVER_PORT=0
export GATEWAY_PORT=0 USER_SERVICE_PORT=0

cleanup() {
    result=$?
    if [ "${result}" -ne 0 ]; then
        docker compose -p "${SMOKE_PROJECT}" logs --tail=80 config-server discovery-server app api-gateway user-service
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
users = base_url('user-service', 8080)

for name in ('tracker-service', 'discovery-server', 'api-gateway', 'user-service'):
    environment = get_json(f'{config}/{name}/default')
    sources = environment['propertySources']
    assert environment['name'] == name
    assert any(f'{name}.yml' in source['name'] for source in sources), sources
    assert any(source['source'].get('info.app.configuration-source') == 'config-server'
               for source in sources), sources
    print(f'Config Server serves shared and service configuration: {name}')

for name, base in (('tracker-service', tracker), ('discovery-server', discovery), ('api-gateway', gateway), ('user-service', users)):
    info = get_json(f'{base}/actuator/info')
    assert info['app']['configuration-source'] == 'config-server', info
    print(f'{name} loaded remote configuration')

deadline = time.monotonic() + 90
while True:
    try:
        for name in ('TRACKER-SERVICE', 'CONFIG-SERVER', 'API-GATEWAY', 'USER-SERVICE'):
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
print('Eureka contains UP instances of both business services, config-server and api-gateway')

request = urllib.request.Request(f'{users}/api/v1/users?page=0&size=20')
with urllib.request.urlopen(request, timeout=5) as response:
    assert response.status == 200
    assert response.headers['X-Total-Count'] == '0'
    assert json.load(response) == []
print('R2DBC user API and its database are available')

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
assert sorted(swagger_config['urls'], key=lambda item: item['name']) == [
    {'name': 'tracker-service', 'url': '/v3/api-docs/tracker-service'},
    {'name': 'user-service', 'url': '/v3/api-docs/user-service'}
], swagger_config
user_spec = get_json(f'{gateway}/v3/api-docs/user-service')
assert user_spec['servers'] == [{'url': '/'}]
assert '/api/v1/users' in user_spec['paths']
assert '/internal/users/resolve' not in user_spec['paths']
spec = get_json(f'{gateway}/v3/api-docs/tracker-service')
assert '/api/v1/users' not in spec['paths']
assert spec['servers'] == [{'url': '/'}], spec.get('servers')
assert all(path in spec['paths'] for path in (
    '/api/v1/projects', '/api/v1/projects/{projectId}/members',
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
print('Gateway serves one Swagger UI, its assets and both OpenAPI specifications')

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


# Exercise legacy transfer only in this fresh, isolated Compose project.
compose = ['docker', 'compose', '-p', project]
subprocess.run(compose + ['stop', 'app', 'user-service', 'api-gateway'], check=True)
legacy_sql = """
INSERT INTO users (id, name, email, role, created_at, updated_at)
VALUES (7000, ('Legacy, O''Brien' || chr(92) || 'User'), 'legacy@example.com', 'TEAM_LEAD',
        '2026-01-01T00:00:00Z', '2026-02-01T00:00:00Z');
INSERT INTO projects (name, code, status) VALUES ('Legacy', 'LEGACY', 'ACTIVE');
INSERT INTO tasks (task_key, title, status, priority, author_id, project_id)
SELECT 'LEGACY-1', 'Historical task', 'TODO', 'MEDIUM', 7000, id FROM projects WHERE code = 'LEGACY';
INSERT INTO project_members (project_id, user_id)
SELECT id, 7000 FROM projects WHERE code = 'LEGACY';
"""
subprocess.run(compose + ['exec', '-T', 'postgres', 'sh', '-c',
    'exec psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
    input=legacy_sql, text=True, check=True)
subprocess.run(['python3', 'scripts/migrate-users.py', '--project', project], check=True)
repeat = subprocess.run(['python3', 'scripts/migrate-users.py', '--project', project],
                        text=True, capture_output=True)
assert repeat.returncode != 0 and 'Target users table must be empty' in repeat.stderr
subprocess.run(compose + ['up', '-d', '--wait', '--wait-timeout', '150', 'app', 'user-service', 'api-gateway'], check=True)
# Docker reassigns published port 0 when a container is restarted.
gateway = base_url('api-gateway', 8080)
tracker = base_url('app', 8080)
users = base_url('user-service', 8080)
api_base = urljoin(gateway, spec['servers'][0]['url']).rstrip('/')
# Health is independent of discovery propagation.
deadline = time.monotonic() + 90
while True:
    try:
        legacy_user, _ = api_request('/api/v1/users/7000')
        break
    except AssertionError:
        if time.monotonic() >= deadline:
            raise
        time.sleep(2)
assert legacy_user['name'] == "Legacy, O'Brien" + chr(92) + "User"
assert legacy_user['createdAt'] == '2026-01-01T00:00:00Z'
assert legacy_user['updatedAt'] == '2026-02-01T00:00:00Z'
history, _ = api_request('/api/v1/tasks')
assert len(history) == 1 and history[0]['authorId'] == 7000
api_request(f'/api/v1/tasks/{history[0]["id"]}', 'DELETE', expected=204)
api_request(f'/api/v1/projects/{history[0]["projectId"]}', 'DELETE', expected=204)
api_request('/api/v1/users/7000', 'DELETE', expected=204)
print('Legacy user ID/timestamps/history transferred; occupied target rejected; sequence advanced')

lead, headers = api_request('/api/v1/users', 'POST', {
    'name': 'Smoke Team Lead', 'email': 'lead@example.com', 'role': 'TEAM_LEAD'
}, expected=201)
assert lead['id'] > 7000, lead
location = headers['Location']
assert urlparse(location).netloc == urlparse(gateway).netloc, location
assert get_json(location)['id'] == lead['id']

def create_project_when_discovered(data):
    deadline = time.monotonic() + 90
    while True:
        try:
            return api_request('/api/v1/projects', 'POST', data, expected=201)
        except AssertionError as error:
            detail = error.args[0]
            if not isinstance(detail, tuple) or detail[1] != 503 or time.monotonic() >= deadline:
                raise
            time.sleep(2)


project, _ = create_project_when_discovered({
    'name': 'Smoke Project', 'code': 'SMOKE', 'status': 'ACTIVE', 'teamLeadId': lead['id']
})
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

# Public user endpoints have moved; the internal endpoint is not exposed by Gateway.
try:
    get_json(f'{tracker}/api/v1/users')
    raise AssertionError('Tracker still publishes the user API')
except urllib.error.HTTPError as error:
    assert error.code == 404
api_request('/internal/users/resolve', 'POST', {'ids': [lead['id']]}, expected=404)

# A real user-service outage must not produce partial writes in the tracker.
subprocess.run(compose + ['stop', 'user-service'], check=True)
error, _ = api_request('/api/v1/projects', 'POST', {
    'name': 'Rejected', 'code': 'REJECTED', 'status': 'ACTIVE', 'teamLeadId': lead['id']
}, expected=503)
assert error['message'] == 'User service is unavailable'
projects, _ = api_request('/api/v1/projects')
assert not any(item['code'] == 'REJECTED' for item in projects)
assert api_request(f'/api/v1/tasks/{task["id"]}')[0]['id'] == task['id']
subprocess.run(compose + ['up', '-d', '--wait', '--wait-timeout', '150', 'user-service'], check=True)
recovered, _ = create_project_when_discovered({
    'name': 'Recovered', 'code': 'RECOVERED', 'status': 'ACTIVE', 'teamLeadId': lead['id']
})

# Move remains a local transaction; the target project has an active lead member.
task, _ = api_request(f'/api/v1/tasks/{task["id"]}/move', 'POST', {
    'projectId': recovered['id'], 'assigneeId': lead['id'],
    'labelIds': [label['id']], 'version': task['version']
})
assert task['projectId'] == recovered['id']
api_request(f'/api/v1/users/{lead["id"]}', 'DELETE', expected=204)
api_request(f'/api/v1/users/{lead["id"]}', expected=404)
assert api_request(f'/api/v1/tasks/{task["id"]}')[0]['authorId'] == lead['id']
member_history, _ = api_request(f'/api/v1/projects/{recovered["id"]}/members')
assert member_history[0]['userId'] == lead['id']
updated, _ = api_request(f'/api/v1/tasks/{task["id"]}', 'PUT', {
    'taskKey': task['taskKey'], 'title': 'Edited history', 'status': task['status'], 'priority': task['priority'],
    'authorId': task['authorId'], 'assigneeId': task['assigneeId'], 'projectId': task['projectId'],
    'labelIds': task['labelIds'], 'version': task['version']
})
assert updated['authorId'] == lead['id'] and updated['assigneeId'] == lead['id']
api_request('/api/v1/tasks', 'POST', {
    'taskKey': 'SMOKE-2', 'title': 'Rejected deleted author', 'status': 'TODO', 'priority': 'MEDIUM',
    'authorId': lead['id'], 'projectId': recovered['id'], 'labelIds': []
}, expected=404)
print('Feign outage/recovery, task move, logical delete and historical references verified')

_, headers = api_request(f'/api/v1/tasks/{task["id"]}', 'DELETE', expected=204)
api_request(f'/api/v1/tasks/{task["id"]}', expected=404)
print('Gateway forwards CRUD, nested routes, pagination, feed, Location and structured errors')
print('Gateway and common Swagger smoke checks passed')
PY
