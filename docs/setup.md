# Setup Guide

## Prerequisites

- **Java 21** (`JAVA_HOME` pointing to JDK 21, e.g. `/opt/homebrew/opt/openjdk@21`)
- **Maven** (`mvn`)
- **Docker** — logged in to a registry the Kubernetes cluster can pull from
- **kubectl** — configured to reach the benchmark cluster (see below)
- **Kubernetes benchmark cluster** — provisioned in the same VPC as the MongoDB EA cluster; see [performance testing setup](https://github.com/mongodb/mdbaas-control-plane/blob/main/docs/performance-testing-setup.md) for how to create the MongoDB EA cluster
- **MongoDB TLS CA certificate** (`ca.pem`) for your self-hosted cluster
- **Voyage AI / MongoDB AI API key** — required for seeding (embedding generation)

---

## 1. Create the Kubernetes benchmark cluster

The benchmark cluster must be in the **same VPC** as the MongoDB EA cluster so the pods can reach the MongoDB nodes directly over private networking.

Follow the [performance testing setup guide](https://github.com/mongodb/mdbaas-control-plane/blob/main/docs/performance-testing-setup.md) to create the MongoDB EA cluster and note its VPC.

Then create the Kubernetes cluster in that VPC. Nebius example:

```bash
# Create the cluster in the same VPC as the MongoDB EA cluster
nebius mk8s cluster create \
  --name benchmark-api \
  --vpc-network-id <vpc-network-id> \
  --subnet-id <subnet-id>

# Add a node group — 3 nodes is enough for 2 pods at 12 vCPU/32 GiB + rolling headroom
nebius mk8s node-group create \
  --cluster-id <cluster-id> \
  --name benchmark-nodes \
  --platform-id standard-v2 \
  --cores 32 \
  --memory 128 \
  --fixed-size 3
```

> Adjust platform, core count, and memory to match what your cloud provider offers. Each pod requests `12 vCPU / 32 GiB`, so each node needs at least that available.

---

## 2. Configure kubectl context

Set your context to the benchmark Kubernetes cluster before running any `kubectl` or deploy commands:

```bash
# Nebius example — download credentials for your cluster
nebius mk8s cluster get-credentials --id <cluster-id> --external

# Verify the active context
kubectl config current-context
```

The deploy script defaults to context `benchmark-api`. Override with `KUBE_CONTEXT=<your-context>` if yours differs.

---

## 3. Configure the deploy environment

Copy the example env file and fill in your values:

```bash
cp deploy/.env.deploy.example deploy/.env.deploy
```

Edit `deploy/.env.deploy`:

| Variable            | Description                                                             |
| ------------------- | ----------------------------------------------------------------------- |
| `IMAGE`             | Container image the cluster can pull (push it first)                    |
| `KUBE_CONTEXT`      | kubectl context (default: `benchmark-api`)                              |
| `NAMESPACE`         | Kubernetes namespace (default: `default`)                               |
| `CA_FILE`           | Local path to your MongoDB TLS CA cert                                  |
| `MONGODB_URI`       | Full MongoDB connection string (use in-pod CA path `/etc/mongo/ca.pem`) |
| `EMBEDDING_API_KEY` | Voyage AI / Atlas AI key — required for seeding                         |

---

## 4. Deploy (2 pods × 12 vCPUs / 32 GB RAM)

The deployment is pre-configured with `replicas: 2` and resource limits of `12 CPU / 32Gi` per pod.

```bash
bash deploy/deploy.sh
```

The script builds and pushes the image, creates the required Kubernetes secrets, applies the Deployment and Service, and waits for the rollout to complete.

### Get pod IPs

```bash
kubectl get pods -l app=mdb-load-test -o wide
```

The `IP` column shows each pod's cluster IP.

---

## 5. Seed data

The MongoDB EA cluster is only reachable inside the VPC, so run the seed from **inside a running pod**. The pod already has `MONGODB_URI`, `EMBEDDING_API_KEY`, the CA truststore, and the JAR at `/app/app.jar`.

```bash
# Get a pod name
POD=$(kubectl get pods -l app=mdb-load-test -o jsonpath='{.items[0].metadata.name}')

# Exec in and run the seed
kubectl exec -it "$POD" -- sh -c '
  SEED_PRODUCTS=500000 SEED_CUSTOMERS=10000 SEED_ORDERS=10000 \
  java $JAVA_OPTS \
    -Dspring.profiles.active=seed \
    -jar /app/app.jar
'
```

This seeds **500 000 products** (with embeddings), **10 000 customers**, and **10 000 orders**, then creates the vector search index.
