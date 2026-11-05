#!/usr/bin/env python3
"""Run the real startup script and integrated flows in an isolated, automatically removed Compose project."""
import json
import os
from pathlib import Path
import subprocess
import socket
import tempfile
import time
import uuid
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
PROJECT = f"lab4-integration-smoke-{int(time.time())}-{os.getpid()}"
LOG_DIR = Path(tempfile.mkdtemp(prefix="lab4-integration-smoke-"))
ENV = dict(os.environ, BOOTSTRAP_ADMIN_ENABLED="true", BOOTSTRAP_ADMIN_EMAIL="admin@example.invalid",
           BOOTSTRAP_ADMIN_PASSWORD="Smoke-password-123!", POSTGRES_DB="tracker", POSTGRES_USER="tracker",
           POSTGRES_PASSWORD="tracker", USER_DB_USERNAME="users", USER_DB_PASSWORD="users",
           NOTIFICATION_DB_USERNAME="notifications", NOTIFICATION_DB_PASSWORD="notifications",
           FILE_DB_USERNAME="files", FILE_DB_PASSWORD="files", FILE_MAX_BYTES="10485760",
           JWT_ISSUER="http://task-tracker", JWT_AUDIENCE="task-tracker-api",
           KAFKA_BOOTSTRAP_SERVERS="kafka-1:9092,kafka-2:9094,kafka-3:9096")


def command(args, capture=False, timeout=900, data=None):
    with (LOG_DIR / "commands.log").open("a") as log:
        result = subprocess.run(args, cwd=ROOT, env=ENV, text=True, stdout=subprocess.PIPE if capture else log,
                                stderr=log, timeout=timeout, check=True, input=data)
    return result.stdout.strip() if capture else None


def until(action, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if action():
                return
        except (AssertionError, urllib.error.URLError, TimeoutError, socket.timeout, ConnectionError):
            pass
        time.sleep(1)
    raise AssertionError("Timed out waiting for the notification flow; see smoke logs")


print(f"Isolated project: {PROJECT}", flush=True)
try:
    with tempfile.TemporaryDirectory(prefix="lab4-notification-keys-") as keys:
        keys = Path(keys)
        # Supply bootstrap/keys only through --env-file, exercising real Compose precedence.
        env_file = keys / "smoke.env"
        env_file.write_text(f"JWT_KEY_DIR={keys}/jwt\nSTARTUP_TIMEOUT_SECONDS=300\n"
                            "BOOTSTRAP_ADMIN_ENABLED=true\nBOOTSTRAP_ADMIN_NAME=IntegrationAdmin\n"
                            "BOOTSTRAP_ADMIN_EMAIL=admin@example.invalid\nBOOTSTRAP_ADMIN_PASSWORD=Smoke-password-123!\n")
        for name in ["JWT_KEY_DIR", "STARTUP_TIMEOUT_SECONDS", "BOOTSTRAP_ADMIN_ENABLED", "BOOTSTRAP_ADMIN_NAME",
                     "BOOTSTRAP_ADMIN_EMAIL", "BOOTSTRAP_ADMIN_PASSWORD", "COMPOSE_PROJECT_NAME", "COMPOSE_FILE", "COMPOSE_PROFILES", "COMPOSE_ENV_FILES"]:
            ENV.pop(name, None)
        override = keys / "compose.yml"
        text = "services:\n"
        for service, port in [("app", None), ("user-service", None), ("config-server", None),
                              ("discovery-server", None), ("postgres", None), ("redis", None), ("api-gateway", 8080), ("pgadmin", 80), ("redisinsight", 5540)]:
            text += f"  {service}:\n    ports: " + ("!reset []\n" if port is None else f"!override ['127.0.0.1::{port}']\n")
        override.write_text(text)
        compose = ["docker", "compose", "--env-file", str(env_file), "-p", PROJECT,
                   "-f", str(ROOT / "docker-compose.yml"), "-f", str(override)]
        try:
            startup = ["bash", "scripts/docker-up.sh", *compose[2:]]
            command(startup)
            private_key = (keys / "jwt/private.pem").read_bytes()
            # Repeated startup must honor disabling bootstrap and retain the signing key/data.
            env_file.write_text(env_file.read_text().replace("BOOTSTRAP_ADMIN_ENABLED=true", "BOOTSTRAP_ADMIN_ENABLED=false"))
            command(startup)
            assert (keys / "jwt/private.pem").read_bytes() == private_key
            enabled = command(compose + ["exec", "-T", "user-service", "printenv", "BOOTSTRAP_ADMIN_ENABLED"], capture=True)
            assert enabled == "false"
            print("Real startup twice: env-file, generated/preserved key and disabled bootstrap: PASS", flush=True)
            address = command(compose + ["port", "api-gateway", "8080"], capture=True)
            base = "http://" + address

            def api(method, path, token=None, body=None, expected=200):
                headers = {"Content-Type": "application/json"}
                if token:
                    headers["Authorization"] = "Bearer " + token
                req = urllib.request.Request(base + path, method=method, headers=headers,
                                             data=None if body is None else json.dumps(body).encode())
                try:
                    response = urllib.request.urlopen(req, timeout=8)
                except urllib.error.HTTPError as error:
                    response = error
                with response:
                    payload = response.read()
                    assert response.code == expected, f"{method} {path}: {response.code}, expected {expected}"
                    return (json.loads(payload) if payload else None), response.headers

            for service in ["user-service", "tracker-service", "notification-service", "file-service"]:
                until(lambda: api("GET", f"/v3/api-docs/{service}")[0].get("openapi"))
            configuration = api("GET", "/v3/api-docs/swagger-config")[0]
            expected_services = {"user-service", "tracker-service", "notification-service", "file-service"}
            assert {(e["name"], e["url"]) for e in configuration["urls"]} == {(n, "/v3/api-docs/" + n) for n in expected_services}
            for name in expected_services:
                spec = api("GET", "/v3/api-docs/" + name)[0]
                assert spec["components"]["securitySchemes"]["bearerAuth"]["scheme"] == "bearer"
                assert spec["paths"] and spec["servers"][0]["url"] == "/"
            for service, port in [("api-gateway", 8080), ("pgadmin", 80), ("redisinsight", 5540)]:
                ui_base = "http://" + command(compose + ["port", service, str(port)], capture=True)
                path = "/swagger-ui/index.html" if service == "api-gateway" else "/"
                def ui_ready():
                    with urllib.request.urlopen(ui_base + path, timeout=8) as response:
                        return response.status == 200
                until(ui_ready)
            hosts = json.loads(command(compose + ["exec", "-T", "pgadmin", "/venv/bin/python3", "-c",
                "import json; print(json.dumps(sorted(s['Host'] for s in json.load(open('/pgadmin4/servers.json'))['Servers'].values())))"], capture=True))
            assert set(hosts) == {"postgres", "users-db", "notifications-db", "files-db"}
            print("Swagger selector/four secured schemas, both UIs and four pgAdmin presets: PASS", flush=True)

            def registrations_ready():
                payload = command(compose + ["exec", "-T", "discovery-server", "wget", "-qO-", "--header=Accept: application/json", "http://localhost:8761/eureka/apps"], capture=True, timeout=15)
                applications = json.loads(payload)["applications"].get("application", [])
                entries = {app["name"]: app["instance"] for app in applications}
                expected = {s.upper() for s in expected_services | {"api-gateway", "config-server"}}
                assert expected <= entries.keys()
                for name in expected:
                    instances = entries[name] if isinstance(entries[name], list) else [entries[name]]
                    assert len([instance for instance in instances if instance["status"] == "UP"]) == 1
                return True
            until(registrations_ready)
            for name in expected_services | {"api-gateway"}:
                payload = command(compose + ["exec", "-T", "config-server", "wget", "-qO-", f"http://localhost:8888/{name}/default"], capture=True, timeout=15)
                configuration = json.loads(payload)
                assert configuration["name"] == name
                sources = [source["name"] for source in configuration["propertySources"]]
                assert any(name + ".yml" in source for source in sources)
                assert any("application.yml" in source for source in sources)
            print("Eureka: six applications UP; Config Server: five service configs plus common config: PASS", flush=True)

            def login(email):
                return api("POST", "/api/v1/auth/login", body={"email": email, "password": "Smoke-password-123!"})[0]["accessToken"]

            admin = login("admin@example.invalid")
            people = []
            for name, role in [("Lead", "TEAM_LEAD"), ("Developer", "DEVELOPER"), ("Other", "DEVELOPER")]:
                email = name.lower() + "@example.invalid"
                person = api("POST", "/api/v1/users", admin,
                             {"name": name, "email": email, "role": role, "password": "Smoke-password-123!"}, 201)[0]
                people.append((person["id"], login(email)))
            (lead_id, lead), (developer_id, developer), (other_id, other) = people
            project = api("POST", "/api/v1/projects", lead,
                          {"name": "Notifications", "code": "NOTICE", "status": "ACTIVE", "teamLeadId": lead_id}, 201)[0]
            for user_id in [developer_id, other_id]:
                api("POST", f"/api/v1/projects/{project['id']}/members", lead, {"userId": user_id}, 201)

            def create_task(key, assignee):
                return api("POST", "/api/v1/tasks", lead,
                           {"taskKey": key, "title": key, "status": "TODO", "priority": "HIGH",
                            "projectId": project["id"], "assigneeId": assignee, "labelIds": []}, 201)[0]

            def notices(token, event_type="TaskAssigned"):
                return [n for n in api("GET", "/api/v1/notifications", token)[0] if n["type"] == event_type]

            def count(token):
                return len(notices(token))

            for token in [lead, developer, other]:
                until(lambda: len(notices(token, "ProjectMemberAdded")) == 1)
            print("Project membership notifications including automatic lead membership: PASS", flush=True)

            api("GET", "/api/v1/notifications", expected=401)
            task = create_task("NOTICE-1", developer_id)
            until(lambda: count(developer) == 1)
            notice = notices(developer)[0]
            assert notice["resourceId"] == str(task["id"]) and "NOTICE-1" in notice["message"]
            assert count(other) == 0 and count(admin) == 0
            api("PATCH", f"/api/v1/notifications/{notice['id']}/read", other, expected=404)
            api("PATCH", f"/api/v1/notifications/{notice['id']}/read", developer, expected=204)
            first_read = notices(developer)[0]["readAt"]
            api("PATCH", f"/api/v1/notifications/{notice['id']}/read", developer, expected=204)
            assert notices(developer)[0]["readAt"] == first_read
            print("Task -> outbox -> Kafka -> owned notification/read through Gateway: PASS", flush=True)

            task = api("PATCH", f"/api/v1/tasks/{task['id']}/status", developer,
                       {"status": "IN_PROGRESS", "version": task["version"]})[0]
            until(lambda: len(notices(lead, "TaskStatusChanged")) == 1)
            body = {key: task[key] for key in ["taskKey", "title", "description", "status", "priority", "projectId", "labelIds", "version"]}
            body["assigneeId"] = other_id
            body["status"] = "DONE"
            api("PUT", f"/api/v1/tasks/{task['id']}", admin, body)
            until(lambda: len(notices(lead, "TaskStatusChanged")) == 2)
            print("Status changes through PATCH and PUT notify the author: PASS", flush=True)
            until(lambda: count(other) == 1)
            command(compose + ["stop", "notification-service"], timeout=60)
            create_task("NOTICE-2", other_id)
            command(compose + ["up", "-d", "--no-deps", "--wait", "--wait-timeout", "120", "notification-service"], timeout=150)
            until(lambda: count(other) == 2)
            print("Reassignment and consumer restart preserve delivery: PASS", flush=True)
            command(compose + ["stop", "kafka-1", "kafka-2", "kafka-3"], timeout=90)
            create_task("NOTICE-3", developer_id)

            def pending_count():
                value = command(compose + ["exec", "-T", "postgres", "psql", "-U", "tracker", "-d", "tracker", "-At", "-c",
                    "SELECT count(*) FROM outbox_events WHERE published_at IS NULL"], capture=True, timeout=15)
                return int(value)

            assert pending_count() >= 1
            command(compose + ["up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "kafka-1", "kafka-2", "kafka-3"], timeout=210)
            until(lambda: count(developer) == 2 and pending_count() == 0)
            print("Kafka outage leaves a pending outbox row; delivery resumes after recovery: PASS", flush=True)

            poison = '{"eventType":"UnknownSmokeEvent"}'
            command(compose + ["exec", "-T", "kafka-1", "/opt/kafka/bin/kafka-console-producer.sh", "--bootstrap-server", "kafka-1:9092",
                    "--topic", "tracker.task.events.v1", "--producer-property", "acks=all"], data=poison+"\n", timeout=30)
            recovered = command(compose + ["exec", "-T", "kafka-1", "/opt/kafka/bin/kafka-console-consumer.sh", "--bootstrap-server", "kafka-1:9092",
                    "--topic", "tracker.task.events.v1.dlt", "--from-beginning", "--max-messages", "1", "--timeout-ms", "30000"], capture=True, timeout=40)
            assert recovered == poison
            create_task("NOTICE-4", other_id)
            until(lambda: count(other) == 3)
            print("Unsupported event reaches replicated DLT; valid events continue: PASS", flush=True)
            def raw_api(method, path, token, data=None, content_type=None, expected=200):
                headers = {"Authorization": "Bearer " + token}
                if content_type:
                    headers["Content-Type"] = content_type
                req = urllib.request.Request(base + path, method=method, headers=headers, data=data)
                try:
                    response = urllib.request.urlopen(req, timeout=30)
                except urllib.error.HTTPError as error:
                    response = error
                with response:
                    payload = response.read()
                    assert response.code == expected, f"{method} {path}: {response.code}, expected {expected}"
                    return payload, response.headers

            def upload(token, payload, expected=201, chunked=False):
                boundary = "lab4-" + uuid.uuid4().hex
                prefix = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="proof.bin"\r\n'
                          'Content-Type: application/octet-stream\r\n\r\n').encode()
                body = prefix + payload + f"\r\n--{boundary}--\r\n".encode()
                data = (body[i:i + 65536] for i in range(0, len(body), 65536)) if chunked else body
                result, headers = raw_api("POST", "/api/v1/files", token, data,
                                          "multipart/form-data; boundary=" + boundary, expected)
                return json.loads(result), headers

            # The largest permitted file exercises multipart spilling, bytea, and Gateway limits.
            content = bytes(range(256)) * 40960
            file, headers = upload(developer, content, chunked=True)
            file_path = "/api/v1/files/" + file["id"]
            assert file["ownerId"] == developer_id and file["size"] == len(content)
            assert headers["Location"] == file_path
            assert api("GET", file_path, developer)[0]["name"] == "proof.bin"
            for token in [developer, admin]:
                downloaded, headers = raw_api("GET", file_path + "/content", token)
                assert downloaded == content and "attachment" in headers["Content-Disposition"]
                assert headers["Content-Type"] == "application/octet-stream"
            api("GET", file_path, other, expected=403)
            api("GET", file_path + "/content", other, expected=403)
            api("GET", file_path, expected=401)
            upload(developer, b"", expected=400)
            upload(developer, content + b"x", expected=413)
            upload(developer, content + b"x", expected=413, chunked=True)
            # Exceed the complete request limit as well, independent of Content-Length.
            upload(developer, content + bytes(70000), expected=413, chunked=True)
            until(lambda: len(notices(developer, "FileUploaded")) == 1)
            assert notices(developer, "FileUploaded")[0]["resourceId"] == file["id"]
            print("10 MiB chunked upload, exact download, access, file/request limits and FileUploaded: PASS", flush=True)

            command(compose + ["up", "-d", "--no-deps", "--force-recreate", "--wait", "--wait-timeout", "120", "files-db"], timeout=150)
            command(compose + ["up", "-d", "--no-deps", "--force-recreate", "--wait", "--wait-timeout", "120", "file-service"], timeout=150)
            until(lambda: raw_api("GET", file_path + "/content", developer)[0] == content)
            print("File bytes survive database and service container recreation on retained volume: PASS", flush=True)
            print("PASS: real full startup, env/key persistence, Eureka/Config/Swagger/UIs, four event types, files, Kafka recovery and DLT", flush=True)
        finally:
            try:
                command(compose + ["logs", "--no-color"], timeout=60)
            finally:
                command(compose + ["down", "--volumes", "--remove-orphans", "--rmi", "local"], timeout=180)
finally:
    print(f"Smoke logs: {LOG_DIR}", flush=True)
