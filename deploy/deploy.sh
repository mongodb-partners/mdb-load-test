#!/usr/bin/env bash
#
# Deploy mdb-load-test to the Nebius Kubernetes cluster "benchmark-api".
#
# Prerequisites:
#   - kubectl configured with a context for the benchmark-api cluster
#       (Nebius: `nebius mk8s cluster get-credentials --id <cluster-id> --external`
#        or download the kubeconfig from the Nebius console).
#   - docker logged in to a registry the cluster can pull from
#       (e.g. Nebius Container Registry: `nebius container registry ...` / `docker login <registry-host>`).
#   - A local CA file for the MongoDB TLS connection (default /home/suresh/ca.pem).
#
# Configure via deploy/.env.deploy (copy from .env.deploy.example) or env vars:
#   IMAGE          registry/repo:tag reachable by the cluster (REQUIRED)
#   MONGODB_URI    full connection string with tlsCAFile=/etc/mongo/ca.pem (REQUIRED)
#   KUBE_CONTEXT   kubectl context           (default: benchmark-api)
#   NAMESPACE      target namespace          (default: default)
#   CA_FILE        local path to ca.pem      (default: /home/suresh/ca.pem)
#   SKIP_IMAGE     "true" to skip build/push (default: false)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load local, gitignored config if present.
if [[ -f "$SCRIPT_DIR/.env.deploy" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/.env.deploy"
  set +a
fi

KUBE_CONTEXT="${KUBE_CONTEXT:-benchmark-api}"
NAMESPACE="${NAMESPACE:-default}"
CA_FILE="${CA_FILE:-/home/suresh/ca.pem}"
SKIP_IMAGE="${SKIP_IMAGE:-false}"

: "${IMAGE:?Set IMAGE to a registry/repo:tag the cluster can pull (see deploy/.env.deploy.example)}"
: "${MONGODB_URI:?Set MONGODB_URI (see deploy/.env.deploy.example)}"

if [[ ! -f "$CA_FILE" ]]; then
  echo "ERROR: CA file not found at '$CA_FILE'. Set CA_FILE to your ca.pem path." >&2
  exit 1
fi

KCTL=(kubectl --context "$KUBE_CONTEXT" -n "$NAMESPACE")

echo "==> Target: context=$KUBE_CONTEXT namespace=$NAMESPACE image=$IMAGE"
"${KCTL[@]}" cluster-info >/dev/null

# 1) Build + push the image.
if [[ "$SKIP_IMAGE" != "true" ]]; then
  echo "==> Building image $IMAGE"
  docker build -t "$IMAGE" "$ROOT_DIR"
  echo "==> Pushing image $IMAGE"
  docker push "$IMAGE"
else
  echo "==> SKIP_IMAGE=true, using existing image $IMAGE"
fi

# 2) Secrets (idempotent via apply).
echo "==> Applying secret mdb-load-test-mongo (MONGODB_URI)"
"${KCTL[@]}" create secret generic mdb-load-test-mongo \
  --from-literal=MONGODB_URI="$MONGODB_URI" \
  --dry-run=client -o yaml | "${KCTL[@]}" apply -f -

echo "==> Applying secret mdb-load-test-ca (ca.pem from $CA_FILE)"
"${KCTL[@]}" create secret generic mdb-load-test-ca \
  --from-file=ca.pem="$CA_FILE" \
  --dry-run=client -o yaml | "${KCTL[@]}" apply -f -

# Optional: embedding key so text /search queries can be embedded at runtime.
# (JMeter sends pre-computed vectors, so this is not required for the load test.)
if [[ -n "${EMBEDDING_API_KEY:-}" ]]; then
  echo "==> Applying secret mdb-load-test-embedding (EMBEDDING_API_KEY)"
  "${KCTL[@]}" create secret generic mdb-load-test-embedding \
    --from-literal=EMBEDDING_API_KEY="$EMBEDDING_API_KEY" \
    --dry-run=client -o yaml | "${KCTL[@]}" apply -f -
else
  echo "==> EMBEDDING_API_KEY not set; skipping embedding secret (text /search will 500 until set)"
fi

# 3) Deployment (substitute image) + service.
echo "==> Applying Deployment and Service"
sed "s|__IMAGE__|${IMAGE}|g" "$SCRIPT_DIR/k8s/deployment.yaml" | "${KCTL[@]}" apply -f -
"${KCTL[@]}" apply -f "$SCRIPT_DIR/k8s/service.yaml"

# 4) Wait for rollout.
echo "==> Waiting for rollout"
"${KCTL[@]}" rollout status deployment/mdb-load-test --timeout=300s

echo "==> Done."
echo "    Pods:   ${KCTL[*]} get pods -l app=mdb-load-test"
echo "    Logs:   ${KCTL[*]} logs -f deploy/mdb-load-test"
echo "    Local:  ${KCTL[*]} port-forward svc/mdb-load-test 8080:80   # then hit http://localhost:8080"
