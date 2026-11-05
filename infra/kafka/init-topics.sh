#!/usr/bin/env bash
set -euo pipefail

bootstrap="${KAFKA_BOOTSTRAP_SERVERS:-kafka-1:9092,kafka-2:9094,kafka-3:9096}"
for base in tracker.task.events.v1 tracker.project.events.v1 files.events.v1; do
    for topic in "$base" "$base.dlt"; do
        /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap" \
            --command-config /etc/kafka/client.properties \
            --create --if-not-exists --topic "$topic" \
            --partitions 3 --replication-factor 3 --config min.insync.replicas=2
        description=$(/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap" \
            --command-config /etc/kafka/client.properties --describe --topic "$topic")
        if ! grep -Eq 'PartitionCount: 3[[:space:]]+ReplicationFactor: 3' <<< "$description" \
                || ! grep -q 'min.insync.replicas=2' <<< "$description"; then
            echo "Unexpected existing topic configuration: $topic (expected partitions=3, RF=3, min ISR=2)" >&2
            exit 1
        fi
        echo "Ready: $topic (partitions=3, RF=3, min ISR=2)"
    done
done
