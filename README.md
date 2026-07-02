# Customer Support Hub

Backend service for managing customer support interactions. Built with Spring Boot 3.x, Spring Data JPA, and MySQL.

Roles: **ADMIN**, **AGENT**, **CUSTOMER** — with role-based access to profiles and tickets.

## Status

Work in progress — see [`PROGRESS.md`](./PROGRESS.md).

## Tech stack

- Java 21
- Spring Boot 3.5.x (Web, Data JPA, Validation, Security, OAuth2 Resource Server)
- MySQL 8.4 + Flyway migrations
- JWT authentication (HS256)
- Gradle (Kotlin DSL) + Docker Compose

## Quick start — Docker

Requires Docker Desktop running.

```
cp .env.example .env
docker compose up --build
```

`.env.example` documents every configurable environment variable. `.env` is gitignored — edit it locally to change database credentials, the JWT signing secret, or the seeded admin's password before the first boot. Docker Compose will refuse to start if any required secret (`MYSQL_*_PASSWORD`, `JWT_SECRET`) is missing.

**Services:**
- `mysql` on host port `3306`, persistent volume `mysql-data`
- `app` on host port `8080`, waits for MySQL healthcheck before booting

**Tear down:**
```
docker compose down       # keep the database
docker compose down -v    # wipe the database too
```

## Local run without Docker

Requires MySQL reachable on `localhost:3306`. All secrets must come from the shell environment — no in-source defaults.

```
DB_URL="jdbc:mysql://localhost:3306/customer_hub" DB_USERNAME=root DB_PASSWORD=root JWT_SECRET="a-very-long-development-only-secret-32-chars-min" ./gradlew bootRun
```

## Build only

```
./gradlew clean bootJar
```

Produces `build/libs/customer-support-hub-0.0.1-SNAPSHOT.jar`.
