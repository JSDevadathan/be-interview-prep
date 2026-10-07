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
| 4 | Product Catalog | |
| 5 | Order Service | |

**Video:**
