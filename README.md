# mdb-load-test

A small, standards-compliant **Spring Boot** service backed by **MongoDB Atlas** for load
testing CRUD + **vector search**. Every endpoint goes through a database base layer that, based on
a global flag (`app.use-db`), either runs the real Mongo query or returns a canned response after a
fixed simulated wait — so you can compare real DB latency against a baseline. A **JMeter** plan
drives a balanced mix of CRUD and vector search, and a **Java seed runner** creates the indexes and
seeds the data. The app **computes 1024-dim Voyage embeddings itself** (via
`https://ai.mongodb.com/v1/embeddings` using an Atlas-issued model key), stores them on each
product, and creates a standard **`vector`** search index — so **no Automated Embedding / storage
auto-scaling** is required on the cluster.

## Architecture

```
Controller  ->  Service (extends BaseDataService)  ->  Repository / MongoTemplate  ->  MongoDB Atlas
                     |
                     +-- app.use-db=false ->  Thread.sleep(app.simulated-wait-ms) + canned response
```

- **Domains:** Products, Orders, Customers.
- **Database base layer:** `BaseDataService.execute(dbCall, simulated)` centralizes the toggle.
- **Vector search:** `$vectorSearch` over `products.embedding`. `/search` accepts either a text
  `query` (the app embeds it via `EmbeddingClient` → `ai.mongodb.com`) **or** a pre-computed
  `vector` (bypasses embedding — used by the load test to isolate DB latency).
- **Indexes:**
  - Regular indexes ensured on startup by `MongoIndexInitializer`
    (unique `sku`, `category`; `customerId`, `status`, `createdAt`; unique `email`).
  - The standard `vector` index `products_vector_index` (path `embedding`, 1024 dims, cosine) is
    created by the seed runner. No Automated Embedding / auto-scaling needed.

## Requirements

- **JDK 21** and **Maven 3.9+** (install a JDK if missing, e.g. `brew install openjdk@21`).
- A **MongoDB Atlas** cluster that supports Vector Search (M10+). **No** Automated Embedding or
  storage auto-scaling required — the app supplies vectors and uses a standard `vector` index.
  Vector search does not run on a local `mongod`.
- An **embedding API key** (`EMBEDDING_API_KEY`):
  - An **Atlas-issued model key** (`al-…`) → default endpoint `https://ai.mongodb.com/v1/embeddings`.
  - Or a **VoyageAI key** (`pa-…`) → set `EMBEDDING_BASE_URL=https://api.voyageai.com/v1/embeddings`.
  Needed by the **seed** (to embed products) and to embed **text** `/search` queries at runtime.
- **Apache JMeter 5.6+** for load testing.

## Configuration

Settings live in `src/main/resources/application.yml` and are overridable via env vars:

| Property | Env var | Default | Notes |
|---|---|---|---|
| `spring.data.mongodb.uri` | `MONGODB_URI` | `mongodb://localhost:27017/loadtest` | Atlas SRV string |
| `app.use-db` | `APP_USE_DB` | `true` | `false` = simulated mode (restart to switch) |
| `app.simulated-wait-ms` | `APP_SIMULATED_WAIT_MS` | `50` | Baseline latency when simulated |
| `app.vector.index-name` | `APP_VECTOR_INDEX` | `products_vector_index` | Must match the seeded index |
| `app.vector.num-dimensions` | `APP_VECTOR_DIMENSIONS` | `1024` | 256 / 512 / 1024 / 2048 |
| `app.vector.quantization` | `APP_VECTOR_QUANTIZATION` | `none` | none / scalar / binary |
| `app.embedding.api-key` | `EMBEDDING_API_KEY` | _(empty)_ | Voyage/Atlas model key |
| `app.embedding.base-url` | `EMBEDDING_BASE_URL` | `https://ai.mongodb.com/v1/embeddings` | Atlas endpoint (or voyageai.com) |
| `app.embedding.model` | `EMBEDDING_MODEL` | `voyage-4` | voyage-4 / -4-large / -4-lite / code-3 |
| `server.port` | `SERVER_PORT` | `8080` | |

### Seed sizing (`app.seed.*`, seed profile only)

| Property | Env var | Default | Notes |
|---|---|---|---|
| `app.seed.products` | `SEED_PRODUCTS` | `1000000` | product count |
| `app.seed.customers` | `SEED_CUSTOMERS` | `10000` | customer count |
| `app.seed.orders` | `SEED_ORDERS` | `20000` | order count |
| `app.seed.insert-batch-size` | `SEED_INSERT_BATCH` | `1000` | docs per bulk insert |
| `app.seed.embed-concurrency` | `SEED_EMBED_CONCURRENCY` | `4` | parallel embedding batches |
| `app.seed.generate-embeddings` | `SEED_GENERATE_EMBEDDINGS` | `true` | `false` = skip embedding (fast, no vectors) |
| `app.seed.product-id-sample-cap` | `SEED_PRODUCT_ID_SAMPLE_CAP` | `50000` | product ids kept in memory for order refs |
| `app.seed.index-only` | `SEED_INDEX_ONLY` | `false` | `true` = only (re)create the vector index |

Copy `.env.example` to `.env` and fill it in.

## Build

```bash
mvn clean package
```

## Seed data + create the vector index

Runs as a batch job (no web server), then exits:

```bash
export MONGODB_URI="mongodb+srv://.../loadtest"
export EMBEDDING_API_KEY="al-..."     # Atlas-issued key (ai.mongodb.com), or pa-... + EMBEDDING_BASE_URL
mvn spring-boot:run -Dspring-boot.run.profiles=seed
# or:  java -jar target/mdb-load-test-0.0.1-SNAPSHOT.jar --spring.profiles.active=seed
```

This clears the collections, seeds customers, products (**embedding each via the embedding API**),
and orders, then creates the standard `vector` index `products_vector_index`. Defaults are
**1,000,000 products / 10,000 customers / 20,000 orders**. Products are embedded in bounded parallel
waves and inserted in batches, so heap stays flat regardless of scale. The index builds
asynchronously in Atlas — allow time before querying.

> ⚠️ **At 1M:** ~7,800 embedding requests to the embedding API — real token cost and time
> (rate limits apply; the client retries 429/5xx with backoff). Raise `SEED_EMBED_CONCURRENCY` if
> your tier allows. 1M × 1024-dim vectors is several GB — size the cluster (M30+ recommended).

**Start small / variants:**
```bash
# smaller run
SEED_PRODUCTS=10000 SEED_CUSTOMERS=1000 SEED_ORDERS=2000 mvn spring-boot:run -Dspring-boot.run.profiles=seed

# only (re)create the vector index, e.g. after data is already embedded
SEED_INDEX_ONLY=true mvn spring-boot:run -Dspring-boot.run.profiles=seed

# seed without embeddings (fast; vector search returns nothing until embedded)
SEED_GENERATE_EMBEDDINGS=false mvn spring-boot:run -Dspring-boot.run.profiles=seed
```

## Run the service

```bash
export MONGODB_URI="mongodb+srv://.../loadtest"
export APP_USE_DB=true          # or false for simulated mode
mvn spring-boot:run
```

## API

Base path `/api`. All bodies are JSON.

### Products `/api/products`
| Method | Path | Purpose |
|---|---|---|
| POST | `/` | create |
| POST | `/bulk` | bulk create (array) |
| GET | `/{id}` | read one |
| POST | `/bulk-get` | bulk read `{ "ids": [...] }` |
| GET | `/?page=&size=` | paged list |
| PUT | `/{id}` | update one |
| PUT | `/bulk` | bulk update (array of `{id, ...}`) |
| POST | `/search` | vector search — text `{ "query":"wireless headphones", "limit":10 }` or vector `{ "vector":[…1024…], "limit":10, "numCandidates":100 }` |

### Orders `/api/orders`
`POST /`, `POST /bulk`, `GET /{id}`, `POST /bulk-get`, `GET /?page=&size=`

### Customers `/api/customers`
`POST /`, `POST /bulk`, `GET /{id}`, `POST /bulk-get`, `GET /?page=&size=`, `PUT /{id}`, `PUT /bulk`

### Quick smoke test
```bash
# create a product
curl -s -XPOST localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1","name":"Test","description":"d","category":"electronics","price":9.99}'

# vector search — text query (app embeds it via the embedding API)
curl -s -XPOST localhost:8080/api/products/search -H 'Content-Type: application/json' \
  -d '{"query":"wireless noise cancelling headphones","limit":5,"numCandidates":100}'

# vector search — pre-computed vector (no embedding call; used by the load test)
curl -s -XPOST localhost:8080/api/products/search -H 'Content-Type: application/json' \
  -d "{\"vector\":[$(python3 -c 'print(",".join(["0.01"]*1024))')],\"limit\":5}"
```

## Load test (JMeter)

The plan `jmeter/loadtest.jmx` runs a **balanced** mix (Create / Read / Update / Search each ~25%)
via Throughput Controllers. The `/search` body sends a **pre-computed random 1024-dim vector** (built
by a Groovy pre-processor each iteration) so the search benchmark hits MongoDB `$vectorSearch`
directly, without the embedding API on the hot path. (For realistic end-to-end search including
embedding, send `{"query":"…text…"}` instead.)

For read/update samplers to hit real documents (in `use-db=true` mode), pass a seeded product/customer id:

```bash
jmeter -n -t jmeter/loadtest.jmx -l results.jtl -e -o jmeter-report \
  -Jhost=localhost -Jport=8080 -Jthreads=50 -Jramp=15 -Jduration=180 \
  -JproductId=<seeded-product-id> -JcustomerId=<seeded-customer-id>
```

Tunable `-J` props: `host, port, threads, ramp, duration, pageSize, searchLimit,
searchNumCandidates, productId, customerId`.

Compare a run with `APP_USE_DB=true` against one with `APP_USE_DB=false` (restart the app between
runs) to isolate DB/search cost from the fixed baseline.

## Deploy to Nebius Kubernetes (`benchmark-api`)

Deploys a **single pod** (`deployment/mdb-load-test`, 1 replica, **12 vCPU / 32Gi**) that connects
to the MongoDB EA replica set running on another cluster over TLS.

Artifacts: `Dockerfile`, `deploy/deploy.sh`, `deploy/k8s/{deployment,service}.yaml`.

**One-time setup**
1. Point `kubectl` at the `benchmark-api` cluster (download its kubeconfig / use the Nebius CLI to
   get credentials) and `docker login` to a registry the cluster can pull from.
2. `cp deploy/.env.deploy.example deploy/.env.deploy` and edit:
   - `IMAGE` → your registry/repo:tag.
   - `MONGODB_URI` → already set to your connection string, but **with two changes** the pod needs:
     `tlsCAFile=/etc/mongo/ca.pem` (in-pod mount path) and a **database** in the path
     (`…:31713/<db>?authSource=admin…`). Set `<db>` to where the benchmark data lives.
   - `CA_FILE` → local path to `ca.pem` (default `/home/suresh/ca.pem`).
   - `EMBEDDING_API_KEY` → your `al-…` key (optional at runtime since JMeter sends vectors; only
     needed to embed **text** `/search` queries in-cluster).

**Deploy**
```bash
./deploy/deploy.sh
```
The script builds & pushes the image, creates secrets — `mdb-load-test-mongo` (the URI),
`mdb-load-test-ca` (`ca.pem` mounted at `/etc/mongo/ca.pem`), and `mdb-load-test-embedding`
(`EMBEDDING_API_KEY`, only if set) — applies the Deployment + Service, and waits for rollout.
Re-runnable (secrets/manifests are applied idempotently). Use `SKIP_IMAGE=true` to redeploy without
rebuilding.

**Connectivity notes**
- The MongoDB hosts are `search-benchmarking-{0,1,2}.external` (NodePorts). If the benchmark-api
  cluster can't resolve `*.external`, uncomment the `hostAliases` block in
  `deploy/k8s/deployment.yaml` and map each name to the other cluster's node IPs.
- TLS uses your custom CA. If the server cert's SAN doesn't include the `*.external` names, add
  `&tlsAllowInvalidHostnames=true` to `MONGODB_URI` (less secure — troubleshooting only).
- The password lives only in a k8s Secret and in `deploy/.env.deploy` (gitignored). Nothing secret
  is committed.

**Drive load at it**
```bash
kubectl --context benchmark-api port-forward svc/mdb-load-test 8080:80
jmeter -n -t jmeter/loadtest.jmx -l results.jtl -Jhost=localhost -Jport=8080 ...
```
(or change the Service to `NodePort` and point JMeter at a node IP).

## Notes / caveats

- **The app computes embeddings** (via `ai.mongodb.com` / VoyageAI) and stores them, then uses a
  standard `vector` index — so **no Automated Embedding or storage auto-scaling** is needed. The
  MongoDB embeddings endpoint is a Preview feature; keys are managed in the Atlas UI.
- **Vector search needs Atlas** (M10+; M30+ for a 1M-vector index). In `use-db=false` mode `/search`
  returns canned hits, so the JMeter plan still runs end-to-end without a search-capable cluster.
- **Index build is async.** After seeding, searches match only once the index is `queryable`
  (watch it in the Atlas UI / `listSearchIndexes`).
- Products created via the API are **not** embedded (only seeded products are), so they won't appear
  in vector-search results — expected for load testing.
- The `app.use-db` flag is global and requires a restart to change (by design).
