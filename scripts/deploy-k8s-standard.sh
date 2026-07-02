#!/usr/bin/env bash
#
# Full build + Kubernetes deploy for orders-service (standard rolling update).
#
# Builds:
#   - Next.js frontend (static export)
#   - Java JAX-RS backend (Maven WAR)
#   - Liberty Docker image
#
# Deploys to Kubernetes (via Ansible):
#   - Namespace, PostgreSQL, Kafka (KRaft) + topics
#   - Single orders-service Deployment (rolling update), Service, Ingress, ServiceMonitor
#
# Usage:
#   ./scripts/deploy-k8s-standard.sh
#   IMAGE_REGISTRY=ghcr.io/myorg INVENTORY=inventory/production/hosts.yml ./scripts/deploy-k8s-standard.sh
#   CLUSTER_TYPE=kind KIND_CLUSTER_NAME=kind ./scripts/deploy-k8s-standard.sh
#   SKIP_BUILD=true ./scripts/deploy-k8s-standard.sh
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# ---------------------------------------------------------------------------
# Configuration (override via environment)
# ---------------------------------------------------------------------------
IMAGE_REGISTRY="${IMAGE_REGISTRY:-my-registry}"
IMAGE_NAME="${IMAGE_NAME:-orders-service}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
PUSH_IMAGES="${PUSH_IMAGES:-true}"
CLUSTER_TYPE="${CLUSTER_TYPE:-remote}"   # remote | kind | minikube
KIND_CLUSTER_NAME="${KIND_CLUSTER_NAME:-kind}"
INVENTORY="${INVENTORY:-inventory/dev/hosts.yml}"
SKIP_BUILD="${SKIP_BUILD:-false}"
SKIP_DEPLOY="${SKIP_DEPLOY:-false}"
SERVER_CONFIG="${SERVER_CONFIG:-server.xml}"
ORDERS_NAMESPACE="${ORDERS_NAMESPACE:-orders}"
KUBECONFIG_PATH="${KUBECONFIG:-${HOME}/.kube/config}"
KUBE_CONTEXT=""

ORDERS_IMAGE="${IMAGE_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"

log()  { printf '\n==> %s\n' "$*"; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

# ---------------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------------
preflight() {
  log "Preflight checks"
  require_cmd mvn
  require_cmd docker
  require_cmd kubectl
  require_cmd ansible-playbook
  require_cmd ansible-galaxy

  if [[ ! -f "${KUBECONFIG_PATH}" ]]; then
    die "Kubeconfig not found at ${KUBECONFIG_PATH}. Set KUBECONFIG or configure kubectl."
  fi

  if ! kubectl --kubeconfig="${KUBECONFIG_PATH}" cluster-info >/dev/null 2>&1; then
    die "Cannot reach Kubernetes cluster. Check KUBECONFIG=${KUBECONFIG_PATH}"
  fi

  KUBE_CONTEXT="$(kubectl --kubeconfig="${KUBECONFIG_PATH}" config current-context 2>/dev/null || echo unknown)"
  if [[ "${PUSH_IMAGES}" == "true" && "${KUBE_CONTEXT}" == "docker-desktop" ]]; then
    log "Docker Desktop cluster detected — using local images (PUSH_IMAGES=false)"
    PUSH_IMAGES="false"
  fi

  log "Cluster: ${KUBE_CONTEXT}"
  log "Image:     ${ORDERS_IMAGE}"
  log "Inventory: ansible/${INVENTORY}"
  log "Push images: ${PUSH_IMAGES}"
}

# ---------------------------------------------------------------------------
# Build frontend + backend WAR
# ---------------------------------------------------------------------------
build_app() {
  if [[ "${SKIP_BUILD}" == "true" ]]; then
    log "Skipping build (SKIP_BUILD=true)"
    return
  fi

  log "Building frontend + backend (Maven WAR)"
  mvn -f orders-service/pom.xml clean package -DskipTests

  [[ -f orders-service/target/orders-service.war ]] \
    || die "WAR not produced at orders-service/target/orders-service.war"
  log "WAR built: orders-service/target/orders-service.war"
}

# ---------------------------------------------------------------------------
# Docker images
# ---------------------------------------------------------------------------
setup_cluster_docker() {
  case "${CLUSTER_TYPE}" in
    minikube)
      log "Using Minikube Docker daemon"
      eval "$(minikube docker-env)"
      PUSH_IMAGES="false"
      ;;
    kind)
      log "Kind cluster detected — images will be loaded with 'kind load'"
      PUSH_IMAGES="false"
      ;;
    remote)
      log "Remote cluster — images will be pushed to registry"
      ;;
    *)
      die "Unknown CLUSTER_TYPE=${CLUSTER_TYPE}. Use remote, kind, or minikube."
      ;;
  esac
}

build_docker_image() {
  log "Building Docker image: ${ORDERS_IMAGE} (config=${SERVER_CONFIG})"
  docker build \
    -f Dockerfile \
    --build-arg "SERVER_CONFIG=${SERVER_CONFIG}" \
    -t "${ORDERS_IMAGE}" \
    .
}

publish_image() {
  if [[ "${PUSH_IMAGES}" == "true" ]]; then
    log "Pushing ${ORDERS_IMAGE}"
    docker push "${ORDERS_IMAGE}"
  elif [[ "${CLUSTER_TYPE}" == "kind" ]]; then
    log "Loading ${ORDERS_IMAGE} into kind cluster '${KIND_CLUSTER_NAME}'"
    kind load docker-image "${ORDERS_IMAGE}" --name "${KIND_CLUSTER_NAME}"
  else
    log "Image ${ORDERS_IMAGE} built locally (PUSH_IMAGES=false, CLUSTER_TYPE=${CLUSTER_TYPE})"
  fi
}

build_images() {
  if [[ "${SKIP_BUILD}" == "true" ]]; then
    log "Skipping Docker build (SKIP_BUILD=true)"
    return
  fi

  setup_cluster_docker
  build_docker_image
  publish_image
}

# ---------------------------------------------------------------------------
# Ansible / Kubernetes deploy
# ---------------------------------------------------------------------------
install_ansible_deps() {
  log "Installing Ansible collections"
  (
    cd ansible
    ansible-galaxy collection install -r requirements.yml
  )
}

deploy_kubernetes() {
  if [[ "${SKIP_DEPLOY}" == "true" ]]; then
    log "Skipping Kubernetes deploy (SKIP_DEPLOY=true)"
    return
  fi

  install_ansible_deps

  local pull_policy="IfNotPresent"
  if [[ "${PUSH_IMAGES}" == "true" ]]; then
    pull_policy="Always"
  elif [[ "${KUBE_CONTEXT}" == "docker-desktop" ]]; then
    pull_policy="Always"
  fi

  log "Deploying full stack to Kubernetes via Ansible (standard rolling update)"
  (
    cd ansible
    ansible-playbook \
      -i "${INVENTORY}" \
      playbooks/deploy-standard.yml \
      -e "orders_image=${ORDERS_IMAGE}" \
      -e "orders_namespace=${ORDERS_NAMESPACE}" \
      -e "orders_image_pull_policy=${pull_policy}" \
      -e "kubeconfig_path=${KUBECONFIG_PATH}" \
      -e "postgres_enabled=true" \
      -e "kafka_enabled=true"
  )
}

# ---------------------------------------------------------------------------
# Post-deploy verification
# ---------------------------------------------------------------------------
verify_deployment() {
  if [[ "${SKIP_DEPLOY}" == "true" ]]; then
    return
  fi

  log "Waiting for orders-service deployment rollout"
  kubectl --kubeconfig="${KUBECONFIG_PATH}" -n "${ORDERS_NAMESPACE}" \
    rollout status deployment/orders-service --timeout=300s

  log "Deployment status"
  kubectl --kubeconfig="${KUBECONFIG_PATH}" -n "${ORDERS_NAMESPACE}" get pods,svc,ingress

  log "Health check (port-forward)"
  kubectl --kubeconfig="${KUBECONFIG_PATH}" -n "${ORDERS_NAMESPACE}" \
    port-forward deployment/orders-service 19080:9080 >/dev/null 2>&1 &
  local pf_pid=$!
  sleep 3
  if curl -sf http://localhost:19080/health/ready >/dev/null 2>&1; then
    log "Readiness probe OK: http://localhost:19080/health/ready"
  else
    log "Readiness probe not yet reachable via port-forward (pods may still be starting)"
  fi
  if curl -sf http://localhost:19080/api/orders/version >/dev/null 2>&1; then
    log "API OK: http://localhost:19080/api/orders/version -> $(curl -sf http://localhost:19080/api/orders/version)"
  else
    log "API not yet reachable at /api/orders/version (check pod logs if this persists)"
  fi
  kill "${pf_pid}" 2>/dev/null || true

  (
    cd ansible
    ansible-playbook \
      -i "${INVENTORY}" \
      playbooks/status-standard.yml \
      -e "kubeconfig_path=${KUBECONFIG_PATH}" \
      -e "orders_namespace=${ORDERS_NAMESPACE}"
  ) >/dev/null 2>&1 || true
}

print_summary() {
  cat <<EOF

================================================================================
 Deploy complete (standard rolling update)
================================================================================
 Namespace:     ${ORDERS_NAMESPACE}
 Image:         ${ORDERS_IMAGE}
 Inventory:     ansible/${INVENTORY}

 Useful commands:
   kubectl -n ${ORDERS_NAMESPACE} get pods
   kubectl -n ${ORDERS_NAMESPACE} port-forward deployment/orders-service 9080:9080
   curl http://localhost:9080/health/ready
   curl http://localhost:9080/api/orders/version

 Update (rebuild + redeploy):
   ./scripts/deploy-k8s-standard.sh

 Status / teardown:
   ./scripts/ansible-k8s.sh status-standard
   ./scripts/ansible-k8s.sh teardown-standard
================================================================================

EOF
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
  preflight
  build_app
  build_images
  deploy_kubernetes
  verify_deployment
  print_summary
}

main "$@"
