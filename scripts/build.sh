#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> Building WAR (backend + Next.js frontend)..."
mvn -f orders-service/pom.xml clean package -DskipTests

echo "==> Building Liberty image..."
docker build -f Dockerfile -t my-registry/orders-service:1.0.0 .

echo "==> Done. Image: my-registry/orders-service:1.0.0"
echo "    Run locally:  docker compose up --build"
echo "    Deploy K8s (blue/green): ./scripts/deploy-k8s.sh"
echo "    Deploy K8s (standard):   ./scripts/deploy-k8s-standard.sh"
