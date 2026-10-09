#!/usr/bin/env python3
"""One-time ID-preserving import into an empty users-db; source stays unchanged."""
import argparse
import csv
import io
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", help="Compose project name; omit for the default project")
    args = parser.parse_args()
    compose = ["docker", "compose"]
    if args.project:
        compose += ["-p", args.project]
    directory = Path(__file__).resolve().parent.parent

    def run(arguments, data=None):
        return subprocess.check_output(compose + arguments, input=data, cwd=directory)

    running = set(run(["ps", "--status", "running", "--services"]).decode().splitlines())
    if running & {"app", "user-service", "api-gateway"}:
        raise SystemExit("Stop app, user-service and api-gateway before importing users.")
    if not {"postgres", "users-db"} <= running:
        raise SystemExit("Start postgres and users-db first; initialize the user-service schema before stopping it.")

    def psql(service, sql):
        return run(["exec", "-T", service, "sh", "-c",
                    'exec psql -X -q -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], sql.encode())

    missing = psql("postgres", """
        SELECT count(*) FROM (
            SELECT author_id AS id FROM tasks
            UNION SELECT assignee_id FROM tasks WHERE assignee_id IS NOT NULL
            UNION SELECT user_id FROM project_members
        ) refs WHERE NOT EXISTS (SELECT 1 FROM users WHERE users.id = refs.id);
    """).strip()
    if missing != b"0":
        raise SystemExit("Legacy users do not cover all historical references; import cancelled.")
    query = """
        SET TIME ZONE 'UTC';
        COPY (SELECT id, name, email, role, created_at, updated_at FROM users ORDER BY id)
        TO STDOUT WITH (FORMAT csv);
    """
    data = psql("postgres", query).decode()
    rows = list(csv.reader(io.StringIO(data)))
    count = len(rows)
    # Escape SQL strings explicitly; source values never become psql commands.
    def literal(value):
        return "'" + value.replace("'", "''") + "'"

    inserts = "\n".join(
        "INSERT INTO users (id, name, email, role, created_at, updated_at) VALUES ("
        + str(int(row[0])) + ", " + ", ".join(literal(value) for value in row[1:]) + ");"
        for row in rows
    )
    sql = """
        BEGIN;
        SET LOCAL TIME ZONE 'UTC';
        SET LOCAL standard_conforming_strings = on;
        LOCK TABLE users IN ACCESS EXCLUSIVE MODE;
        DO $$ BEGIN
            IF EXISTS (SELECT 1 FROM users) THEN
                RAISE EXCEPTION 'Target users table must be empty';
            END IF;
        END $$;
    """ + inserts + f"""
        DO $$ BEGIN
            IF (SELECT count(*) FROM users) <> {count} THEN
                RAISE EXCEPTION 'Imported row count does not match';
            END IF;
        END $$;
        SELECT setval(pg_get_serial_sequence('users', 'id'),
                      COALESCE((SELECT max(id) FROM users), 1), EXISTS (SELECT 1 FROM users));
        COMMIT;
    """
    psql("users-db", sql)
    if psql("users-db", query).decode() != data:
        raise SystemExit("Import committed, but readback differs; keep application services stopped and investigate.")
    print(f"Imported and verified {count} users with original IDs and timestamps; source retained.")


if __name__ == "__main__":
    main()
