#!/usr/bin/env python3
"""Build and test the notification flow in an isolated, automatically removed Compose project."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
PROJECT = f"lab4-notifications-smoke-{int(time.time())}-{os.getpid()}"
LOG_DIR = Path(tempfile.mkdtemp(prefix="lab4-notifications-smoke-"))
ENV = dict(os.environ, BOOTSTRAP_ADMIN_ENABLED="true", BOOTSTRAP_ADMIN_EMAIL="admin@example.invalid",
           BOOTSTRAP_ADMIN_PASSWORD="Smoke-password-123!", POSTGRES_DB="tracker", POSTGRES_USER="tracker",
           POSTGRES_PASSWORD="tracker", USER_DB_USERNAME="users", USER_DB_PASSWORD="users",
           NOTIFICATION_DB_USERNAME="notifications", NOTIFICATION_DB_PASSWORD="notifications",
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
        except (AssertionError, urllib.error.URLError, TimeoutError):
            pass
        time.sleep(1)
    raise AssertionError("Timed out waiting for the notification flow; see smoke logs")


print(f"Isolated project: {PROJECT}", flush=True)
try:
    with tempfile.TemporaryDirectory(prefix="lab4-notification-keys-") as keys:
        keys = Path(keys)
        command(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(keys / "private.pem")])
        command(["openssl", "pkey", "-in", str(keys / "private.pem"), "-pubout", "-out", str(keys / "public.pem")])
        # Test keys only. Allow the unprivileged container user to read the bind mount.
        (keys / "private.pem").chmod(0o644)
        override = keys / "compose.yml"
        text = "services:\n"
        for service, port in [("app", None), ("user-service", None), ("config-server", None),
                              ("discovery-server", None), ("postgres", None), ("redis", None), ("api-gateway", 8080)]:
            text += f"  {service}:\n    ports: " + ("!reset []\n" if port is None else f"!override ['127.0.0.1::{port}']\n")
        for service in ["app", "api-gateway", "user-service", "notification-service"]:
            # Merge repeated service keys by appending volumes to the existing section where necessary.
            target = f"  {service}:\n"
            volumes = f"    volumes:\n      - {keys}/public.pem:/app/keys/public.pem:ro\n"
            if service == "user-service":
                volumes += f"      - {keys}/private.pem:/app/keys/private.pem:ro\n"
            if target in text:
                text = text.replace(target, target + volumes, 1)
            else:
                text += target + volumes
        override.write_text(text)
        compose = ["docker", "compose", "--env-file", "/dev/null", "-p", PROJECT,
                   "-f", str(ROOT / "docker-compose.yml"), "-f", str(override)]
        try:
            command(compose + ["up", "--build", "-d", "api-gateway", "redis",
                               "kafka-1", "kafka-2", "kafka-3", "kafka-init"])
            init_id = command(compose + ["ps", "-aq", "kafka-init"], capture=True)
            assert command(["docker", "wait", init_id], capture=True, timeout=180) == "0"
            command(compose + ["up", "-d", "--wait", "--wait-timeout", "300", "api-gateway", "redis", "kafka-1", "kafka-2", "kafka-3"], timeout=360)
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

            for service in ["user-service", "tracker-service", "notification-service"]:
                until(lambda: api("GET", f"/v3/api-docs/{service}")[0].get("openapi"))
            print("All three OpenAPI routes are available through Gateway", flush=True)

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
            print("PASS: three event types, JWT, ownership/read, consumer restart, Kafka outage and DLT", flush=True)
        finally:
            try:
                command(compose + ["logs", "--no-color"], timeout=60)
            finally:
                command(compose + ["down", "--volumes", "--remove-orphans", "--rmi", "local"], timeout=180)
finally:
    print(f"Smoke logs: {LOG_DIR}", flush=True)
