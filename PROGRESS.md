# Progress

## Current status
Phase 2 — done. Starting Phase 3.

## Phase checklist
- [x] Phase 0 — Scaffolding
- [x] Phase 1 — Docker
- [x] Phase 2 — Schema & entities
- [ ] Phase 3 — Auth module (login, password change, JWT, admin seeder)
- [ ] Phase 4 — Profile module
- [ ] Phase 5 — Agent + Customer management
- [ ] Phase 6 — Ticket management
- [ ] Phase 7 — Tests hardening
- [ ] Phase 8 — Docs & polish

## Decisions log
- Java 21 / Gradle (Kotlin DSL) / Package `com.surense.customerhub`
- Spring Boot 3.5.16 (latest 3.x supported by Initializr as of 2026-07)
- JWT via `/auth/login` (interviewer approved instead of OAuth)
- Flyway for schema
- No pagination, no Swagger (interviewer said not required)
- Admin seeded via `CommandLineRunner` (guarded by `app.seed.admin.enabled`)
- `users` ↔ `credentials` split (SRP; no accidental hash leakage; MFA/rotation ready)
- `user_roles` as separate table (Spring Security authorities model; multi-role capable)
- `customers` as sub-table with NOT NULL `agent_id`
- Updatable profile fields: `full_name`, `email`. Password change is its own endpoint in auth
- Non-updatable: `username`, `role`, relationships
- Gradle wrapper committed (`gradle-wrapper.jar` + `.properties`); build via `./gradlew`
- Multi-stage Dockerfile (JDK 21 builder → JRE 21 runtime, runs as non-root `app` user)
- Docker Compose: `mysql:8.4` + `app`, `depends_on` waits on MySQL healthcheck, persistent `mysql-data` volume
- Dev defaults live in `.env.example` (committed template) → reviewer runs `cp .env.example .env` before `docker compose up`. `.env` is gitignored. `docker-compose.yml` uses `${VAR:?...}` for required secrets so a missing `.env` fails loudly.
- `plain.jar` disabled in Gradle so Dockerfile can unambiguously copy the bootJar
- Flyway V1 schema: `users`, `credentials` (shared PK via `@MapsId`), `user_roles` (own table w/ unique `(user_id, role)`), `customers` (shared PK, NOT NULL `agent_id`), `tickets` w/ `(customer_id, created_at)` index
- Entities use Lombok + Hibernate `@CreationTimestamp` / `@UpdateTimestamp` (Instant fields)
- Slice tests: H2 in `MODE=MySQL` with Hibernate `create-drop`; Flyway disabled in tests (its DDL is MySQL-specific)
- Repo method `findAllByCustomer_UserId` uses `_` path syntax because `Customer.@Id` field is `userId` (from `@MapsId`), not `id`
- **Non-negotiable rule: internal `BIGINT id` never appears in any URL or response body.** Enforced across all DTOs.
- Two-ID pattern applied only where the API actually needs an external identifier:
  - `tickets.external_id` — `BINARY(16)` UUIDv4, appears in `/tickets/{id}` URL and every ticket response.
  - `users` — no `external_id`. All user-facing DTOs use natural keys (`username`, `email`) as identifiers; the internal `id` is never exposed.
  - `customers`, `credentials`, `user_roles` — never referenced externally, no UUID.
