#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KAFKA_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
TOPICS_LIST="${KAFKA_TOPICS_LIST:-${KAFKA_DIR}/topics.list}"
KAFKA_TOPICS_BIN="${KAFKA_TOPICS_BIN:-/opt/kafka/bin/kafka-topics.sh}"

echo "Waiting for Kafka broker at ${BOOTSTRAP}..."
for i in $(seq 1 30); do
  if "${KAFKA_TOPICS_BIN}" --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; then
    break
  fi
  if [[ "${i}" -eq 30 ]]; then
    echo "Kafka broker not reachable at ${BOOTSTRAP}" >&2
    exit 1
  fi
  sleep 2
done

if [[ ! -f "${TOPICS_LIST}" ]]; then
  echo "Topics list not found: ${TOPICS_LIST}" >&2
  exit 1
fi

while read -r name partitions replication configs; do
  [[ -z "${name}" || "${name}" == \#* ]] && continue

  args=(
    --bootstrap-server "${BOOTSTRAP}"
    --create
    --if-not-exists
    --topic "${name}"
    --partitions "${partitions}"
    --replication-factor "${replication}"
  )

  if [[ -n "${configs}" ]]; then
    read -r -a config_array <<< "${configs}"
    for cfg in "${config_array[@]}"; do
      args+=(--config "${cfg}")
    done
  fi

  echo "Ensuring topic '${name}' (partitions=${partitions}, rf=${replication})..."
  "${KAFKA_TOPICS_BIN}" "${args[@]}"
done < "${TOPICS_LIST}"

echo "Topics:"
"${KAFKA_TOPICS_BIN}" --bootstrap-server "${BOOTSTRAP}" --list
