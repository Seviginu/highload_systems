#!/usr/bin/env bash
# Always creates and removes its own Compose project; never targets the working stack.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
smoke_project="lab4-kafka-smoke-$(date +%s)-$$"
smoke_logs="$(mktemp -d "${TMPDIR:-/tmp}/lab4-kafka-smoke.XXXXXX")"
client_broker=kafka-1
bootstrap=kafka-1:9092

compose() {
    docker compose --env-file /dev/null -p "$smoke_project" -f "$PROJECT_DIR/docker-compose.yml" "$@"
}
cleanup() {
    status=$?
    trap - EXIT
    compose logs --no-color > "$smoke_logs/containers.log" 2>&1 || true
    compose down --volumes --remove-orphans > "$smoke_logs/cleanup.log" 2>&1 || status=1
    echo "Kafka smoke logs: $smoke_logs"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

cli() {
    local command=$1
    shift
    compose exec -T "$client_broker" env KAFKA_HEAP_OPTS='-Xms32m -Xmx128m' "/opt/kafka/bin/$command" "$@"
}
describe() {
    cli kafka-topics.sh --bootstrap-server "$bootstrap" \
        --command-config /etc/kafka/client.properties --describe
}
# Check every partition, including internal topics once they have been created.
wait_for_isr() {
    local expected=$1 deadline=$((SECONDS + 120))
    while (( SECONDS < deadline )); do
        if describe > "$smoke_logs/topics.txt" 2>> "$smoke_logs/client.log" && awk -v expected="$expected" '
            /PartitionCount:/ {
                headers++
                if ($0 !~ /PartitionCount: 3[[:space:]]+ReplicationFactor: 3/) bad=1
            }
            /Partition:/ {
                partitions++
                for (i=1; i<=NF; i++) {
                    if ($i=="Leader:" && $(i+1)<1) bad=1
                    if ($i=="Isr:" && split($(i+1), replicas, ",")!=expected) bad=1
                }
            }
            END { exit !(headers>=6 && partitions==headers*3 && !bad) }
        ' "$smoke_logs/topics.txt"; then
            echo "All partitions: RF=3, ISR=$expected"
            return
        fi
        sleep 2
    done
    cat "$smoke_logs/topics.txt" >&2
    echo "Timed out waiting for ISR=$expected" >&2
    return 1
}
produce() {
    printf 'probe:%s\n' "$1" | cli kafka-console-producer.sh --bootstrap-server "$bootstrap" \
        --topic tracker.task.events.v1 --sync --property parse.key=true --property key.separator=: \
        --producer-property acks=all --producer-property enable.idempotence=true \
        --producer-property request.timeout.ms=5000 --producer-property delivery.timeout.ms=20000 \
        --producer-property max.block.ms=15000 2>> "$smoke_logs/client.log"
}
consume() {
    cli kafka-console-consumer.sh --bootstrap-server "$bootstrap" --topic tracker.task.events.v1 \
        --group "$1" --from-beginning --max-messages "$2" --timeout-ms 30000 \
        2>> "$smoke_logs/client.log"
}

echo "Starting isolated project: $smoke_project"
compose up -d kafka-1 kafka-2 kafka-3 kafka-init
init_exit=$(docker wait "$(compose ps -aq kafka-init)")
[[ "$init_exit" == 0 ]]
compose up -d --wait --wait-timeout 180 kafka-1 kafka-2 kafka-3
# A second execution must succeed without creating duplicates or changing the topic layout.
compose run --rm --no-deps kafka-init > "$smoke_logs/repeated-init.log" 2>&1
wait_for_isr 3
cp "$smoke_logs/topics.txt" "$smoke_logs/topics-initial.txt"

produce before-failure
# Create __consumer_offsets while all three brokers are available.
[[ "$(consume before-failure 1)" == before-failure ]]
wait_for_isr 3
partition=$(cli kafka-get-offsets.sh --bootstrap-server "$bootstrap" --topic tracker.task.events.v1 \
    --command-config /etc/kafka/client.properties --time -1 | awk -F: '$3==1 {print $2}')
[[ "$partition" =~ ^[0-2]$ ]]
leader=$(cli kafka-topics.sh --bootstrap-server "$bootstrap" \
    --command-config /etc/kafka/client.properties --describe --topic tracker.task.events.v1 | \
    awk -v partition="$partition" '/Partition:/ { for(i=1;i<=NF;i++) { if($i=="Partition:") p=$(i+1); if($i=="Leader:") l=$(i+1) } if(p==partition) print l }')
[[ "$leader" =~ ^[1-3]$ ]]
# Run the CLI in a surviving broker, then stop the actual leader of the written partition.
if [[ "$leader" == 1 ]]; then client_broker=kafka-2; else client_broker=kafka-1; fi
bootstrap="$client_broker:9092"
echo "Stopping leader kafka-$leader of task partition $partition"
compose stop -t 10 "kafka-$leader"
wait_for_isr 2
cp "$smoke_logs/topics.txt" "$smoke_logs/topics-with-one-broker-down.txt"
produce during-failure
[[ "$(consume during-failure 2)" == $'before-failure\nduring-failure' ]]
echo "acks=all produce and grouped consume passed with one broker down"

compose up -d --no-deps --force-recreate "kafka-$leader"
wait_for_isr 3
produce after-recovery
# Recreate all containers on their retained volumes, then verify all records survived.
compose up -d --no-deps --force-recreate --wait --wait-timeout 180 kafka-1 kafka-2 kafka-3
wait_for_isr 3
[[ "$(consume after-recovery 3)" == $'before-failure\nduring-failure\nafter-recovery' ]]
cp "$smoke_logs/topics.txt" "$smoke_logs/topics-restored.txt"
echo "PASS: six topics, repeated init, leader failover, grouped consume, ISR recovery and volume persistence"
