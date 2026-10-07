# be-interview-prep

Five Spring Boot features built for the Backend Interview Prep assignment. Each feature was shipped as its own pull request into `main`.

## Stack

- Java 17+ and Spring Boot 3.5
- Maven (wrapper included, no local install needed)
- H2 in-memory database
- JUnit 5 and Spring Boot Test

## Run the app

The app needs a JWT signing secret of at least 32 bytes. To get an admin account, also set an admin username and password:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD='choose-a-strong-password'
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` instead of `./mvnw`. The app starts on `http://localhost:8080` with an in-memory H2 database, so data resets on every restart.

| Variable | Required | Purpose |
|----------|----------|---------|
| `JWT_SECRET` | Yes | HMAC key that signs login tokens. The app refuses to start without it. |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | No, but set both or neither | Creates an ADMIN account at startup. Registration only ever creates USER accounts. |

## Authentication

Every endpoint needs a bearer token except register, login and the public short-link redirect (`GET /{code}`). A token is valid for 15 minutes.

| Method | Path | Access |
|--------|------|--------|
| `POST` | `/api/auth/register` | Public. Creates a USER. |
| `POST` | `/api/auth/login` | Public. Returns `accessToken`. |
| `GET` | `/api/users/me` | Any logged-in user |
| `GET` | `/api/users` | ADMIN only |

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json'   -d '{"username": "alice", "password": "correct-horse-battery"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json'   -d '{"username": "alice", "password": "correct-horse-battery"}' | jq -r .accessToken)
curl localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

A missing, invalid or expired token returns `401` and a missing role returns `403`. Both come back as `application/problem+json`.

## Product catalog

100 products are seeded at startup. Any logged-in user can read them; updating and deleting need ADMIN.

| Method | Path | Access |
|--------|------|--------|
| `GET` | `/api/products` | Any logged-in user |
| `GET` | `/api/products/{id}` | Any logged-in user |
| `PUT` | `/api/products/{id}` | ADMIN only |
| `DELETE` | `/api/products/{id}` | ADMIN only |

The list takes `page` (from 0), `size` (default 20, capped at 100) and `sort` (`field,asc|desc`, repeatable, on `id`, `name`, `category`, `price`, `stock`, `rating` or `createdAt`). Optional filters combine freely: `category` (exact, case-insensitive), `minPrice`, `maxPrice`, `inStock=true` and `name` (substring, case-insensitive). The response holds `content`, `page`, `size`, `totalElements` and `totalPages`.

```bash
curl "localhost:8080/api/products?category=electronics&minPrice=100&maxPrice=450&inStock=true&name=smart&sort=price,desc" \
  -H "Authorization: Bearer $TOKEN"
```

Single-product lookups are cached in memory (Caffeine). An update or delete evicts the entry as soon as its transaction commits, so lookups after that never see the old product, even when one was loading it at the same moment. `ProductLookupCacheTest` proves the cache works by counting SQL statements with Hibernate statistics: five lookups of the same product run one query. `ProductLookupCacheConcurrencyTest` covers a lookup racing an update. The cache is local to each app instance, so running several instances would need a shared cache.

## Order service

Any logged-in user can place orders against the product catalog and see or cancel their own orders. Another customer's order returns `404`.

| Method | Path | Access |
|--------|------|--------|
| `POST` | `/api/orders` | Any logged-in user. Needs an `Idempotency-Key` header. |
| `GET` | `/api/orders/{id}` | The customer who placed the order |
| `POST` | `/api/orders/{id}/cancel` | The customer who placed the order |

```bash
curl -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 6f1c2e9a-checkout-42' \
  -d '{"items": [{"productId": 1, "quantity": 2}, {"productId": 2, "quantity": 1}]}'
```

An order lists 1 to 50 items, each a distinct `productId` with a `quantity` from 1 to 1000. It is all-or-nothing: every item's stock is reserved in one transaction, so if any item is short the whole order fails with `409` (`Insufficient stock for product 2: requested 3`) and no stock is taken. An unknown product returns `404`.

**No overselling.** Each item is reserved with one conditional update, `stock = stock - n where stock >= n`. The database checks and decrements in a single locked step, so two orders can never both see the last unit. Items are reserved in product id order, so orders that share products cannot deadlock. An admin `PUT /api/products/{id}` locks the product row, so an order placed at the same moment waits and then reserves from the new stock instead of being overwritten. If a request waits too long for a lock, it returns `503` with `Retry-After`, and nothing is changed.

**Retries.** The client generates a unique `Idempotency-Key` (for example a UUID) per order and sends the same key when it retries. Keys are scoped to the logged-in customer and backed by a unique constraint:

| Retry | Response |
|-------|----------|
| Same key, same items, after the order was created | `200` with the original order. No stock is taken again. |
| Same key, sent while the first copy is still in flight | One copy commits. The other hits the unique constraint, rolls back and returns the same order. |
| Same key, different items | `422`, because the key was already used for a different request |
| Same key after a failed attempt (for example `409`) | Treated as new, since a failed attempt stores nothing |

A new order returns `201` with a `Location` header. A missing or blank key returns `400`.

**Cancelling** sets the order to `CANCELLED` and returns its stock. The order row is locked while this happens, so cancelling twice, even at the same moment, returns the stock only once. A second cancel returns the cancelled order with `200`.

Placing or cancelling an order evicts the affected products from the lookup cache after the transaction commits, so `GET /api/products/{id}` always shows the current stock. Deleting a product that has orders returns `409`.

`OrderConcurrencyTest` fires 50 simultaneous orders at a product with stock 10 and checks that exactly 10 succeed and the stock ends at 0. It also checks three more races: 10 simultaneous copies of one request create one order, 10 simultaneous cancels return the stock once, and an admin update cannot erase a reservation made while it runs. `OrderControllerIntegrationTest` covers retries, `409`, all-or-nothing, validation, cancelling and ownership over HTTP.

## Run the tests

The tests use their own signing secret from `src/test/resources/config/application.properties`, so no environment variables are needed.

```bash
./mvnw test
```

To run the full build, including tests, as CI does:

```bash
./mvnw verify
```

## Questions

| # | Question | PR link |
|---|----------|---------|
| 1 | Task Manager API | [#15](https://github.com/JSDevadathan/be-interview-prep/pull/15) |
| 2 | URL Shortener | [#16](https://github.com/JSDevadathan/be-interview-prep/pull/16) |
| 3 | Authentication & Roles | [#17](https://github.com/JSDevadathan/be-interview-prep/pull/17) |
| 4 | Product Catalog | [#18](https://github.com/JSDevadathan/be-interview-prep/pull/18) |
| 5 | Order Service | |

**Video:**
