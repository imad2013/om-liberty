#!/usr/bin/env bash
#
# Full build + Kubernetes deploy for orders-service.
#
# Builds:
#   - Next.js frontend (static export)
#   - Java JAX-RS backend (Maven WAR)
#   - Liberty Docker image(s)
#
# Deploys to Kubernetes (via Ansible):
#   - Namespace, PostgreSQL, Kafka (KRaft) + topics
#   - orders-blue / orders-green, Service, Ingress, ServiceMonitor
#
# Usage:
#   ./scripts/deploy-k8s.sh
#   IMAGE_REGISTRY=ghcr.io/myorg INVENTORY=inventory/production/hosts.yml ./scripts/deploy-k8s.sh
#   CLUSTER_TYPE=kind KIND_CLUSTER_NAME=kind ./scripts/deploy-k8s.sh
#   BUILD_GREEN=true ./scripts/deploy-k8s.sh
#   SKIP_BUILD=true ./scripts/deploy-k8s.sh
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# ---------------------------------------------------------------------------
# Configuration (override via environment)
# ---------------------------------------------------------------------------
IMAGE_REGISTRY="${IMAGE_REGISTRY:-my-registry}"
IMAGE_NAME="${IMAGE_NAME:-orders-service}"
IMAGE_TAG_BLUE="${IMAGE_TAG_BLUE:-latest}"
IMAGE_TAG_GREEN="${IMAGE_TAG_GREEN:-latest-green}"
BUILD_GREEN="${BUILD_GREEN:-false}"
PUSH_IMAGES="${PUSH_IMAGES:-true}"
CLUSTER_TYPE="${CLUSTER_TYPE:-remote}"   # remote | kind | minikube
KIND_CLUSTER_NAME="${KIND_CLUSTER_NAME:-kind}"
INVENTORY="${INVENTORY:-inventory/dev/hosts.yml}"
SKIP_BUILD="${SKIP_BUILD:-false}"
SKIP_DEPLOY="${SKIP_DEPLOY:-false}"
SERVER_CONFIG_BLUE="${SERVER_CONFIG_BLUE:-server.xml}"
SERVER_CONFIG_GREEN="${SERVER_CONFIG_GREEN:-server-hardened.xml}"
ORDERS_NAMESPACE="${ORDERS_NAMESPACE:-orders}"
KUBECONFIG_PATH="${KUBECONFIG:-${HOME}/.kube/config}"
KUBE_CONTEXT=""

BLUE_IMAGE="${IMAGE_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG_BLUE}"
GREEN_IMAGE="${IMAGE_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG_GREEN}"

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
  log "Blue image:  ${BLUE_IMAGE}"
  log "Green image: ${GREEN_IMAGE}"
  log "Inventory:   ansible/${INVENTORY}"
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
  local tag="$1"
  local server_config="$2"
  log "Building Docker image: ${tag} (config=${server_config})"
  docker build \
    -f Dockerfile \
    --build-arg "SERVER_CONFIG=${server_config}" \
    -t "${tag}" \
    .
}

publish_image() {
  local tag="$1"
  if [[ "${PUSH_IMAGES}" == "true" ]]; then
    log "Pushing ${tag}"
    docker push "${tag}"
  elif [[ "${CLUSTER_TYPE}" == "kind" ]]; then
    log "Loading ${tag} into kind cluster '${KIND_CLUSTER_NAME}'"
    kind load docker-image "${tag}" --name "${KIND_CLUSTER_NAME}"
  else
    log "Image ${tag} built locally (PUSH_IMAGES=false, CLUSTER_TYPE=${CLUSTER_TYPE})"
  fi
}

build_images() {
  if [[ "${SKIP_BUILD}" == "true" ]]; then
    log "Skipping Docker build (SKIP_BUILD=true)"
    if [[ "${BUILD_GREEN}" != "true" ]]; then
      GREEN_IMAGE="${BLUE_IMAGE}"
    fi
    return
  fi

  setup_cluster_docker
  build_docker_image "${BLUE_IMAGE}" "${SERVER_CONFIG_BLUE}"
  docker tag "${BLUE_IMAGE}" "${IMAGE_REGISTRY}/${IMAGE_NAME}:latest"
  publish_image "${BLUE_IMAGE}"
  publish_image "${IMAGE_REGISTRY}/${IMAGE_NAME}:latest"

  if [[ "${BUILD_GREEN}" == "true" ]]; then
    build_docker_image "${GREEN_IMAGE}" "${SERVER_CONFIG_GREEN}"
    publish_image "${GREEN_IMAGE}"
  else
    log "Skipping green image build (set BUILD_GREEN=true to build v2/hardened image)"
    GREEN_IMAGE="${BLUE_IMAGE}"
  fi
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

  log "Deploying full stack to Kubernetes via Ansible"
  (
    cd ansible
    ansible-playbook \
      -i "${INVENTORY}" \
      playbooks/deploy.yml \
      -e "orders_blue_image=${BLUE_IMAGE}" \
      -e "orders_green_image=${GREEN_IMAGE}" \
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

  log "Waiting for orders-blue deployment"
  kubectl --kubeconfig="${KUBECONFIG_PATH}" -n "${ORDERS_NAMESPACE}" \
    rollout status deployment/orders-blue --timeout=300s

  log "Deployment status"
  kubectl --kubeconfig="${KUBECONFIG_PATH}" -n "${ORDERS_NAMESPACE}" get pods,svc,ingress

  log "Health check (port-forward)"
  kubectl --kubeconfig="${KUBECONFIG_PATH}" -n "${ORDERS_NAMESPACE}" \
    port-forward deployment/orders-blue 19080:9080 >/dev/null 2>&1 &
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
      playbooks/status.yml \
      -e "kubeconfig_path=${KUBECONFIG_PATH}" \
      -e "orders_namespace=${ORDERS_NAMESPACE}"
  ) >/dev/null 2>&1 || true
}

print_summary() {
  cat <<EOF

================================================================================
 Deploy complete
================================================================================
 Namespace:     ${ORDERS_NAMESPACE}
 Blue image:    ${BLUE_IMAGE}
 Green image:   ${GREEN_IMAGE}
 Inventory:     ansible/${INVENTORY}

 Useful commands:
   kubectl -n ${ORDERS_NAMESPACE} get pods
   kubectl -n ${ORDERS_NAMESPACE} port-forward deployment/orders-blue 9080:9080
   curl http://localhost:9080/health/ready
   curl http://localhost:9080/api/orders/version

 Blue/green rollout:
   ./scripts/ansible-k8s.sh rollout

 Teardown:
   ./scripts/ansible-k8s.sh teardown
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
