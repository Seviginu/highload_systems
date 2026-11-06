#!/usr/bin/env bash
# Runs isolated projects sequentially; each smoke removes only its own resources.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"
acceptance_logs=$(mktemp -d "${TMPDIR:-/tmp}/lab4-acceptance.XXXXXX")
trap 'status=$?; echo "Acceptance logs: $acceptance_logs"; exit "$status"' EXIT

echo "[1/3] Maven clean verify (JUnit/Testcontainers/ArchUnit/JaCoCo)"
mvn -B clean verify > "$acceptance_logs/maven.log" 2>&1
echo "[2/3] Full Compose startup and API/event/file/failover acceptance"
python3 "$SCRIPT_DIR/notifications-smoke.py" > "$acceptance_logs/integration.log" 2>&1
echo "[3/3] Kafka topology, leader failover and retained volumes"
bash "$SCRIPT_DIR/kafka-smoke.sh" > "$acceptance_logs/kafka.log" 2>&1

python3 - "$PROJECT_DIR" "$acceptance_logs" <<'PY'
from pathlib import Path
import datetime
import json
import sys
import xml.etree.ElementTree as ET
root, logs = map(Path, sys.argv[1:])
modules = {}
totals = dict(tests=0, failures=0, errors=0, skipped=0)
covered = missed = 0
for pom in sorted(root.glob('*/pom.xml')):
    module = pom.parent
    counts = dict.fromkeys(totals, 0)
    for folder in ['surefire-reports', 'failsafe-reports']:
        for report in (module / 'target' / folder).glob('TEST-*.xml'):
            suite = ET.parse(report).getroot()
            for key in counts:
                counts[key] += int(suite.get(key, 0))
    for key in counts:
        totals[key] += counts[key]
    report = module / 'target/site/jacoco/jacoco.xml'
    coverage = None
    if report.exists():
        line = next(c for c in ET.parse(report).getroot().findall('counter') if c.get('type') == 'LINE')
        c, m = int(line.get('covered')), int(line.get('missed'))
        covered += c
        missed += m
        coverage = round(100 * c / (c + m), 2)
        assert coverage >= 70, module.name
    modules[module.name] = dict(**counts, lineCoveragePercent=coverage)
assert totals['tests'] > 0 and not any(totals[k] for k in ['failures', 'errors', 'skipped'])
result = dict(status='PASS', completedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              checks=dict(maven=True, fullCompose=True, kafkaTopologyAndVolumes=True),
              totals=totals, lineCoveragePercent=round(100 * covered / (covered + missed), 2), modules=modules)
(logs / 'summary.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print('PASS:', totals['tests'], 'tests; coverage', result['lineCoveragePercent'], '%; both isolated Docker checks passed')
print('Summary:', logs / 'summary.json')
PY
