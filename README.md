# Customer Support Hub

Backend service for managing customer support interactions. Built with Spring Boot 3.x, Spring Data JPA, and MySQL.

Roles: **ADMIN**, **AGENT**, **CUSTOMER** — with role-based access to profiles and tickets.

## Status

Work in progress — see [`PROGRESS.md`](./PROGRESS.md).

## Tech stack

- Java 21
- Spring Boot 3.5.x (Web, Data JPA, Validation, Security, OAuth2 Resource Server)
- MySQL 8 + Flyway migrations
- JWT authentication (HS256)
- Docker / Docker Compose (added in Phase 1)

## Build

```bash
./gradlew build -x test
```

## Run (local — requires MySQL reachable on `localhost:3306`)

```bash
./gradlew bootRun
```

Environment variables of interest:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/customer_hub?...` | JDBC URL |
| `DB_USERNAME` | `root` | DB user |
| `DB_PASSWORD` | `root` | DB password |
| `SEED_ADMIN_ENABLED` | `true` | Seed one ADMIN on first boot |
| `SEED_ADMIN_USERNAME` | `admin` | Seed admin username |
| `SEED_ADMIN_PASSWORD` | `admin` | Seed admin password (change in prod) |
| `JWT_SECRET` | dev default | HS256 signing key (≥ 32 chars) |

Docker Compose setup lands in Phase 1.
