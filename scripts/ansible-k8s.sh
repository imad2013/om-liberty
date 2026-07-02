#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/ansible"

ACTION="${1:-deploy}"
INVENTORY="${INVENTORY:-inventory/dev/hosts.yml}"
EXTRA_ARGS=("${@:2}")

install_deps() {
  if ! ansible-galaxy collection list kubernetes.core &>/dev/null; then
    echo "==> Installing Ansible collections..."
    ansible-galaxy collection install -r requirements.yml
  fi
}

run_playbook() {
  local playbook="$1"
  shift
  ansible-playbook -i "$INVENTORY" "playbooks/${playbook}" "$@"
}

install_deps

case "$ACTION" in
  deploy)
    run_playbook deploy.yml "${EXTRA_ARGS[@]}"
    ;;
  deploy-standard)
    run_playbook deploy-standard.yml "${EXTRA_ARGS[@]}"
    ;;
  rollout)
    run_playbook rollout.yml -e orders_target_color=green "${EXTRA_ARGS[@]}"
    ;;
  rollback)
    run_playbook rollback.yml "${EXTRA_ARGS[@]}"
    ;;
  status)
    run_playbook status.yml "${EXTRA_ARGS[@]}"
    ;;
  status-standard)
    run_playbook status-standard.yml "${EXTRA_ARGS[@]}"
    ;;
  teardown)
    run_playbook teardown.yml "${EXTRA_ARGS[@]}"
    ;;
  teardown-standard)
    run_playbook teardown-standard.yml "${EXTRA_ARGS[@]}"
    ;;
  site)
    run_playbook site.yml "${EXTRA_ARGS[@]}"
    ;;
  *)
    echo "Usage: $0 {deploy|deploy-standard|rollout|rollback|status|status-standard|teardown|teardown-standard|site} [ansible-playbook args...]"
    echo "  INVENTORY=inventory/production/hosts.yml $0 deploy"
    echo "  INVENTORY=inventory/production/hosts.yml $0 deploy-standard"
    exit 1
    ;;
esac
