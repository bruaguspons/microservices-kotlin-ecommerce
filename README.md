# Kotlin Ecommerce Microservices

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud%20Gateway-6DB33F?style=flat&logo=spring&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=flat&logo=spring&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=flat&logo=rabbitmq&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203-85EA2D?style=flat&logo=swagger&logoColor=black)

Ecommerce platform built with Kotlin 2.3.21 (Java 25 target) and Spring Boot 4.1.2, composed of independently deployable microservices (product, cart, user), each keeping hexagonal architecture (ports & adapters) internally. Multi-module Maven reactor with database-per-service, an API gateway, and async eventing via RabbitMQ + transactional outbox.

## Stack

- **Kotlin 2.3.21** (Java 25 target) + **Spring Boot 4.1.2**
- **Spring Cloud Gateway** (WebFlux) — single entry point / routing
- **Spring Data JPA** + **Spring Validation**
- **Spring AMQP (RabbitMQ)** — transactional outbox + async domain events
- **Resilience4j** — circuit breaker / retry for cross-service calls
- **jackson-module-kotlin**, `kotlin-spring` and `kotlin-jpa` compiler plugins
- **springdoc-openapi 3.0.2** — Swagger UI (per service)
- **Spring Actuator** — health endpoints
- **H2** (dev, in-memory per service) / **PostgreSQL 16** (prod, one instance per service)

## Architecture

Multi-module Maven reactor:

```
microservices-kotlin-ecommerce/
  pom.xml                    # parent aggregator (packaging=pom)
  buildingblocks/            # shared library: JWT/JWKS, outbox, event contracts, exceptions
  api-gateway/                # Spring Cloud Gateway — routing + auth header enforcement
  services/
    product-service/          # product bounded context, own DB
    cart-service/               # shopping-cart + line-item bounded context, own DB
    user-service/                 # user + auth bounded context, JWT issuance, own DB
```

Each service keeps hexagonal architecture per bounded context:

```
{domain}/
  domain/
    model/          # Pure domain objects (no JPA, no framework)
    valueobject/    # Typed IDs as data classes wrapping UUID
    enums/
  application/
    port/
      in/           # *ServicePort — inbound interface
      out/          # *RepositoryPort — outbound interface (+ out/client for cross-service calls)
    service/        # *ServiceImpl — business logic
    dto/
      command/      # Input data classes (CreateXCommandDTO, UpdateXCommandDTO)
      result/       # Output data classes (*ResultDTO with companion from() factory)
    exception/
  infrastructure/
    api/            # @RestController
    persistence/
      entity/       # @Entity (JPA)
      mapper/       # *PersistenceMapper
      repository/   # *Repository implements *RepositoryPort
    client/         # WebClient adapters for cross-service reads (cart-service only)
    event/          # Outbox publishers / RabbitMQ listeners
```

### Services

| Service | Package | Base path (via gateway) | Own DB | Internal port |
|---|---|---|---|---|
| product-service | `product` | `/api/products` | `product_db` | 8081 |
| cart-service | `cart` | `/api/shopping-carts` (+ `/items`) | `cart_db` | 8082 |
| user-service | `user` | `/api/users`, `/api/admin/users`, `/api/auth`, `/.well-known/jwks.json` | `user_db` | 8083 |
| api-gateway | — | single entry point | — | 8080 |

`buildingblocks` is a shared library (not deployable) consumed by all three services.

## Getting started

### Prerequisites

- Java 25
- Maven (or use the included `./mvnw` wrapper)
- Docker + Docker Compose

There are two ways to run the app. **Option A (Docker)** is the simplest — one command brings up everything. **Option B (local)** runs each JVM process on your machine directly (faster feedback loop while developing, but more manual steps).

### Option A — Full stack with Docker Compose

1. Generate the JWT signing key (only once — it's gitignored, never committed). `user-service` mounts it as a Docker secret to sign JWTs with RS256:

   ```bash
   mkdir -p secrets
   openssl genrsa -out secrets/jwt_private_key.pem 2048
   ```

   If the file is missing, `user-service` falls back to an ephemeral in-memory RSA key on startup (fine for a single run, but tokens won't survive a restart and a WARN is logged).

2. Build and start everything:

   ```bash
   docker compose up --build
   ```

   This starts `product-db`, `cart-db`, `user-db` (Postgres 16, one per service), `rabbitmq`, and the four app containers (`product-service`, `cart-service`, `user-service`, `api-gateway`). Each app service waits for its DB (and RabbitMQ, where applicable) to be healthy before starting. The gateway is the single entry point on **http://localhost:8080**, routing to `/api/products/**`, `/api/shopping-carts/**`, `/api/users/**`, `/api/admin/users/**`, `/api/auth/**`, and `/.well-known/jwks.json`.

3. Stop the stack:

   ```bash
   docker compose down
   ```

   To also remove persisted database volumes:

   ```bash
   docker compose down -v
   ```

### Option B — Local development (H2 in-memory, no Docker Compose)

1. Install `buildingblocks` into your local Maven repo. `spring-boot:run` on a single module does not build its sibling modules — it only resolves them from `~/.m2`, so this step is required before the first run (and again after changing `buildingblocks` code):

   ```bash
   ./mvnw install -pl buildingblocks -am -DskipTests
   ```

2. Start RabbitMQ in Docker. `cart-service` (and any service reacting to domain events) starts a RabbitMQ listener on boot regardless of profile — it's not optional/conditional in code. Without a broker at `localhost:5672` the app still starts and serves HTTP fine, but the listener retries the connection forever and floods the log with `AmqpConnectException` noise:

   ```bash
   docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
   ```

   (Management UI at http://localhost:15672, default guest/guest. Skip this step if you don't need async eventing for what you're testing — the log noise is harmless.)

3. Run each service in its own terminal (each uses H2 in-memory by default, no DB setup needed):

   ```bash
   ./mvnw spring-boot:run -pl services/product-service   # :8081
   ./mvnw spring-boot:run -pl services/user-service       # :8083
   ./mvnw spring-boot:run -pl services/cart-service        # :8082
   ./mvnw spring-boot:run -pl api-gateway                   # :8080
   ```

   `cart-service` calls `product-service` and `user-service` over HTTP, and all three validate JWTs against `user-service`'s JWKS endpoint — run all three (plus the gateway, if you want the single-entry-point routing) for full functionality. Starting order doesn't matter; each client has retry/circuit-breaker via Resilience4j.

4. Stop RabbitMQ when done:

   ```bash
   docker stop rabbitmq && docker rm rabbitmq
   ```

| URL | Description |
|---|---|
| http://localhost:8080 | api-gateway — single entry point (Option A, or Option B step 3) |
| http://localhost:8081/swagger-ui.html | product-service Swagger UI |
| http://localhost:8082/swagger-ui.html | cart-service Swagger UI |
| http://localhost:8083/swagger-ui.html | user-service Swagger UI |
| http://localhost:808X/actuator/health | Health check per service |

## Running tests

```bash
# whole reactor
./mvnw test

# a single module
./mvnw test -pl services/product-service

# a single test class in a module
./mvnw test -pl services/product-service -Dtest=ClassName
```

## Building

```bash
# whole reactor
./mvnw clean package

# a single module and its dependencies
./mvnw clean package -pl services/product-service -am
```

Each service produces its own JAR under `services/{service}/target/`.

## API usage

See [API_USAGE.md](./API_USAGE.md) for how to call the services (via the gateway or directly), how to log in, seeded dev-profile credentials, and the full endpoint reference per service.

## Entity-Relationship Model

See [MER.md](./MER.md) for the ER diagram and table definitions (per-service databases split along bounded-context lines, with cross-aggregate JPA relations replaced by ID references + cross-service calls).
