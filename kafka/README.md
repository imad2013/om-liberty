# Kafka — KRaft mode (no ZooKeeper)

Apache Kafka runs in **KRaft combined mode** (broker + controller in one node). ZooKeeper is **not** used.

## Layout

```
kafka/
├── kraft/server.properties   # Reference broker settings
├── topics.yml                # Human-readable topic spec (docs/Ansible source)
├── topics.list               # Machine-readable topic spec for create-topics.sh
└── scripts/create-topics.sh  # Idempotent topic provisioning
```

## Topics

| Topic | Partitions | Replication | Config |
|-------|------------|-------------|--------|
| `orders-events` | 3 | 1 | 7-day retention, delete cleanup, producer compression |

`auto.create.topics.enable=false` — topics must be provisioned explicitly.

## Docker Compose

Kafka starts with KRaft, then `kafka-init` creates topics before `orders-service` starts:

```bash
docker compose up --build
```

Host clients connect on **`localhost:9094`** (`PLAINTEXT_HOST`). In-container clients use **`kafka:9092`**.

Verify topics:

```bash
docker exec orders-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic orders-events
```

Re-run topic provisioning manually:

```bash
docker compose run --rm kafka-init
```

## Kubernetes

```bash
kubectl apply -f k8s/kafka/kafka-deployment.yaml
kubectl apply -f k8s/kafka/kafka-service.yaml
kubectl apply -f k8s/kafka/kafka-topics-configmap.yaml
kubectl apply -f k8s/kafka/kafka-topic-job.yaml
```

Or use Ansible (`kafka_enabled: true` in inventory) — deploy playbook provisions broker + topics automatically.

## Add a new topic

1. Add a line to `kafka/topics.list`
2. Mirror the entry in `kafka/topics.yml` and `ansible` `kafka_topics` var
3. Re-run `kafka-init` (Compose) or the `kafka-init-topics` Job (K8s/Ansible)

Example line:

```
orders-audit 3 1 retention.ms=2592000000 cleanup.policy=delete
```
