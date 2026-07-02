# Ansible — Kubernetes deployment automation

Ansible playbooks to deploy, manage, and operate the orders-service blue/green rollout on Kubernetes.

**Full build + deploy:** use `../scripts/deploy-k8s.sh` from the repo root (builds frontend/backend, Docker image, then runs this deploy playbook).

## Prerequisites

- Ansible 2.15+
- `kubectl` configured for your target cluster
- Python 3 with `kubernetes` client library (installed by the collection)

```bash
cd ansible
ansible-galaxy collection install -r requirements.yml
pip install kubernetes  # if not already available to Ansible
```

## Layout

```
ansible/
├── ansible.cfg
├── requirements.yml
├── inventory/
│   ├── dev/group_vars/all.yml
│   └── production/group_vars/all.yml
├── playbooks/
│   ├── deploy.yml      # Full stack deploy
│   ├── rollout.yml     # Switch traffic to green (default)
│   ├── rollback.yml    # Switch traffic back to blue
│   ├── status.yml      # Show deployment health
│   └── teardown.yml    # Remove all resources
└── roles/orders-k8s/
    ├── defaults/main.yml
    ├── templates/*.yaml.j2
    └── tasks/
```

## Quick start

From the repo root:

```bash
chmod +x scripts/ansible-k8s.sh

# Deploy namespace, blue/green deployments, service, ingress, ServiceMonitor
./scripts/ansible-k8s.sh deploy

# Check status
./scripts/ansible-k8s.sh status

# Blue → green rollout (waits for green to be ready)
./scripts/ansible-k8s.sh rollout

# Roll back to blue
./scripts/ansible-k8s.sh rollback

# Remove everything
./scripts/ansible-k8s.sh teardown
```

## Direct playbook usage

```bash
cd ansible

# Dev cluster (default inventory)
ansible-playbook -i inventory/dev/hosts.yml playbooks/deploy.yml

# Production cluster
ansible-playbook -i inventory/production/hosts.yml playbooks/deploy.yml

# Custom image tags
ansible-playbook -i inventory/dev/hosts.yml playbooks/deploy.yml \
  -e orders_blue_image=my-registry/orders-service:1.0.0 \
  -e orders_green_image=my-registry/orders-service:2.0.0

# Rollout to green with longer timeout
ansible-playbook -i inventory/production/hosts.yml playbooks/rollout.yml \
  -e orders_rollout_wait_timeout=600

# Rollout to a specific color
ansible-playbook -i inventory/dev/hosts.yml playbooks/rollout.yml \
  -e orders_target_color=green
```

## Key variables

| Variable | Default | Description |
|----------|---------|-------------|
| `orders_namespace` | `orders` | Kubernetes namespace |
| `orders_active_color` | `blue` | Traffic color on initial deploy |
| `orders_target_color` | `green` | Target color for rollout playbook |
| `orders_blue_image` | `my-registry/orders-service:1.0.0` | Blue deployment image |
| `orders_green_image` | `my-registry/orders-service:2.0.0` | Green deployment image |
| `orders_blue_replicas` | `1` (dev) / `3` (prod) | Blue replica count |
| `orders_green_replicas` | `1` (dev) / `3` (prod) | Green replica count |
| `orders_ingress_host` | `orders.example.com` | Ingress hostname |
| `orders_servicemonitor_enabled` | `true` | Deploy Prometheus ServiceMonitor |
| `kubeconfig_path` | `$KUBECONFIG` or `~/.kube/config` | Cluster credentials |
| `kafka_enabled` | `true` | Deploy KRaft Kafka + provision topics |
| `kafka_image` | `apache/kafka:latest` | Official Apache Kafka image (KRaft) |
| `kafka_topics` | `orders-events` (3 partitions) | Topics created by init Job |

Override via inventory `group_vars`, `-e` extra vars, or environment:

```bash
export KUBECONFIG=~/.kube/prod-config
INVENTORY=inventory/production/hosts.yml ./scripts/ansible-k8s.sh deploy
```

## Blue/green procedure

1. **Deploy** both colors (blue receives traffic via Service selector):

   ```bash
   ./scripts/ansible-k8s.sh deploy
   ```

2. **Validate green** is ready (playbook waits automatically):

   ```bash
   ./scripts/ansible-k8s.sh status
   kubectl -n orders port-forward deploy/orders-green 9080:9080
   curl http://localhost:9080/health/ready
   ```

3. **Switch traffic** to green:

   ```bash
   ./scripts/ansible-k8s.sh rollout
   ```

4. **Rollback** if needed:

   ```bash
   ./scripts/ansible-k8s.sh rollback
   ```

The rollout playbook patches the `orders-service` Service selector (`color: blue` → `color: green`) after confirming the target Deployment is ready.

## Teardown options

```bash
# Remove workloads but keep namespace
ansible-playbook -i inventory/dev/hosts.yml playbooks/teardown.yml \
  -e orders_teardown_delete_namespace=false
```
