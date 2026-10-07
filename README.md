# be-interview-prep

Five Spring Boot features built for the Backend Interview Prep assignment. Each feature was shipped as its own pull request into `main`.

## Stack

- Java 17+ and Spring Boot 3.5
- Maven (wrapper included, no local install needed)
- H2 in-memory database
- JUnit 5 and Spring Boot Test

## Run the app

```bash
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` instead of `./mvnw`. The app starts on `http://localhost:8080` with an in-memory H2 database, so no setup is required and data resets on every restart.

## Run the tests

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
| 1 | Task Manager API | |
| 2 | URL Shortener | |
| 3 | Authentication & Roles | |
| 4 | Product Catalog | |
| 5 | Order Service | |

**Video:**
