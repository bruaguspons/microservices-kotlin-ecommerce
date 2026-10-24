# API Usage Guide

How to call the services — directly or through the API gateway — including login and authenticated requests.

## Two ways to call the API

| Path | When to use it |
|---|---|
| **Via api-gateway** (`http://localhost:8080`) | The intended single entry point. All routes below are reachable through it. |
| **Direct to a service** (`http://localhost:8081/8082/8083`) | Useful for isolated debugging of one service, or its own Swagger UI. Bypasses the gateway's header-presence check, but each service still validates the JWT independently (defense-in-depth). |

Both paths accept the same request/response bodies — only the host:port changes.

## 1. Log in

`POST /api/auth/login` — public, no `Authorization` header required.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@ecommerce.dev", "password": "Admin123!"}'
```

Response:

```json
{ "token": "eyJhbGciOiJSUzI1NiIs..." }
```

### Seeded accounts (dev profile only)

`DataSeeder` (one per service, `@Profile("dev")`) creates fake accounts on `user-service` startup so you can log in immediately without registering first:

| Email | Password | Role |
|---|---|---|
| `admin@ecommerce.dev` | `Admin123!` | ADMIN |
| `user@ecommerce.dev` | `User123!` | USER |
| 10 random `firstname.lastnameN@domain` addresses (logged on startup) | `User123!` | USER |

`product-service` and `cart-service` also seed fake catalog/cart data on startup under the `dev` profile.

## 2. Use the token

Every non-public endpoint requires `Authorization: Bearer <token>`. The gateway rejects requests missing this header before they ever reach a service; each service additionally validates the JWT's signature (RS256) and expiry against `user-service`'s JWKS endpoint.

```bash
TOKEN="eyJhbGciOiJSUzI1NiIs..."

curl http://localhost:8080/api/users/<id> \
  -H "Authorization: Bearer $TOKEN"
```

### Public routes (no token needed)

- `GET /.well-known/jwks.json`
- `POST /api/auth/login`
- `POST /api/users` (self-registration)
- `GET /api/products`, `GET /api/products/**`

Everything else requires a valid token.

## 3. Register a new user

`POST /api/users` — public, always assigns role `USER` regardless of input.

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "new.user@example.com",
    "firstname": "New",
    "lastname": "User",
    "age": 28,
    "password": "SomePass123!"
  }'
```

Then log in with those same credentials via `POST /api/auth/login`.

### Updating a user

`PUT /api/users/{id}` is a full replace — it requires `email`, `firstname`, `lastname`, and `age` all in the body, even to change just one field:

```bash
curl -X PUT http://localhost:8080/api/users/<id> \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "new.user@example.com",
    "firstname": "New",
    "lastname": "User",
    "age": 29
  }'
```

## 4. Endpoint reference

### user-service (`/api/users`, `/api/admin/users`, `/api/auth`)

| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/login` | Public |
| POST | `/api/users` | Public (register, role forced to USER) |
| GET | `/api/users/{id}` | Self or ADMIN |
| GET | `/api/users/email/{email}` | ADMIN |
| GET | `/api/users` | ADMIN |
| PUT | `/api/users/{id}` | Self or ADMIN |
| DELETE | `/api/users/{id}` | Self or ADMIN |
| POST | `/api/admin/users` | ADMIN (create with explicit role) |
| PATCH | `/api/admin/users/{id}/role` | ADMIN |

### product-service (`/api/products`)

| Method | Path | Auth |
|---|---|---|
| GET | `/api/products` | Public |
| GET | `/api/products/{id}` | Public |
| POST | `/api/products` | ADMIN |
| PUT | `/api/products/{id}` | ADMIN |
| DELETE | `/api/products/{id}` | ADMIN |

### cart-service (`/api/shopping-carts`)

| Method | Path | Auth |
|---|---|---|
| GET | `/api/shopping-carts` | Cart owner or ADMIN |
| GET | `/api/shopping-carts/{id}` | Cart owner or ADMIN |
| POST | `/api/shopping-carts` | Authenticated |
| POST | `/api/shopping-carts/{id}/pay` | Cart owner or ADMIN |
| DELETE | `/api/shopping-carts/{id}` | Cart owner or ADMIN |
| POST | `/api/shopping-carts/{cartId}/items` | Cart owner or ADMIN |
| PATCH | `/api/shopping-carts/{cartId}/items/{itemId}` | Cart owner or ADMIN |
| DELETE | `/api/shopping-carts/{cartId}/items/{itemId}` | Cart owner or ADMIN |

## 5. Swagger UI (per service)

Swagger only documents each service in isolation (not through the gateway), but is useful for exploring request/response shapes and trying calls with a bearer token pasted into the "Authorize" dialog:

- http://localhost:8081/swagger-ui.html — product-service
- http://localhost:8082/swagger-ui.html — cart-service
- http://localhost:8083/swagger-ui.html — user-service
