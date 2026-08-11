# Makefile for mdb-load-test
# Common tasks: build, run, seed, load test, containerize, deploy.
# Override any variable on the CLI, e.g.  make seed SEED_PRODUCTS=10000
# Recipes also source ./.env if present (so you can keep MONGODB_URI etc there).
SHELL := /bin/bash

# --- Toolchain (java on PATH is the macOS stub; pin the JDK) ---
JAVA_HOME ?= /opt/homebrew/opt/openjdk@21
export JAVA_HOME
JAVA := $(JAVA_HOME)/bin/java
MVN  ?= mvn

JAR := target/mdb-load-test-0.0.1-SNAPSHOT.jar

# --- Runtime config (or set in .env) ---
APP_USE_DB            ?= true
APP_SIMULATED_WAIT_MS ?= 50
SERVER_PORT           ?= 8080
export APP_USE_DB APP_SIMULATED_WAIT_MS SERVER_PORT

# --- Seed sizing ---
SEED_PRODUCTS  ?= 1000000
SEED_CUSTOMERS ?= 10000
SEED_ORDERS    ?= 20000
export SEED_PRODUCTS SEED_CUSTOMERS SEED_ORDERS

# --- Container image ---
IMAGE ?= mdb-load-test:latest

# --- JMeter load-test params ---
HOST        ?= localhost
PORT        ?= 8080
THREADS     ?= 20
RAMP        ?= 10
DURATION    ?= 120
PRODUCT_ID  ?= REPLACE_WITH_SEEDED_ID
CUSTOMER_ID ?= REPLACE_WITH_SEEDED_ID

# Load .env into a recipe shell if present (absent .env is a no-op).
LOAD_ENV := set -a; [ -f .env ] && . ./.env; set +a

.DEFAULT_GOAL := help
.PHONY: help build compile test clean run run-sim seed seed-small seed-index-only \
        docker-build docker-run deploy loadtest

help: ## Show this help
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

## --- build ---
build: ## Package the app (skip tests) -> target/*.jar
	$(MVN) -q -DskipTests package

compile: ## Compile only
	$(MVN) -q -DskipTests compile

test: ## Run tests
	$(MVN) test

clean: ## Remove build output and load-test results
	$(MVN) -q clean
	@rm -f results.jtl jmeter.log
	@rm -rf jmeter-report

$(JAR):
	$(MVN) -q -DskipTests package

## --- run ---
run: build ## Run the API against MongoDB (set MONGODB_URI, or use .env)
	@$(LOAD_ENV); $(JAVA) -jar $(JAR)

run-sim: build ## Run the API in simulated mode (no DB)
	@$(LOAD_ENV); APP_USE_DB=false $(JAVA) -jar $(JAR)

## --- seed ---
seed: build ## Seed data + create the vector index (needs MONGODB_URI + EMBEDDING_API_KEY)
	@$(LOAD_ENV); $(JAVA) -jar $(JAR) --spring.profiles.active=seed

seed-small: build ## Seed a small dataset (10k/1k/2k)
	@$(LOAD_ENV); SEED_PRODUCTS=10000 SEED_CUSTOMERS=1000 SEED_ORDERS=2000 \
		$(JAVA) -jar $(JAR) --spring.profiles.active=seed

seed-index-only: build ## Only (re)create the vector index; skip data
	@$(LOAD_ENV); SEED_INDEX_ONLY=true $(JAVA) -jar $(JAR) --spring.profiles.active=seed

## --- container / deploy ---
docker-build: ## Build the container image ($(IMAGE))
	docker build -t $(IMAGE) .

docker-run: ## Run the image locally in simulated mode on :$(SERVER_PORT)
	docker run --rm -p $(SERVER_PORT):8080 -e APP_USE_DB=false $(IMAGE)

deploy: ## Deploy to the Nebius benchmark-api cluster (deploy/deploy.sh)
	./deploy/deploy.sh

## --- load test ---
loadtest: ## Run the JMeter plan headless -> results.jtl (set PORT/THREADS/PRODUCT_ID/…)
	@$(LOAD_ENV); jmeter -n -t jmeter/loadtest.jmx -l results.jtl \
		-Jhost=$(HOST) -Jport=$(PORT) -Jthreads=$(THREADS) -Jramp=$(RAMP) -Jduration=$(DURATION) \
		-JproductId=$(PRODUCT_ID) -JcustomerId=$(CUSTOMER_ID)
