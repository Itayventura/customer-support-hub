# Progress

## Current status
Phase 6 — done. Starting Phase 7 (test hardening) or 8 (docs).

## Phase checklist
- [x] Phase 0 — Scaffolding
- [x] Phase 1 — Docker
- [x] Phase 2 — Schema & entities
- [x] Phase 3 — Auth module (login, password change, JWT, admin seeder)
- [x] Phase 4 — Profile module
- [x] Phase 5 — Agent + Customer management
- [x] Phase 6 — Ticket management
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
- **JWT `sub` = username** (natural key). No user id in the token payload. Server code resolves username → `User` per request via `CurrentUserService` — one extra DB lookup, but preserves the "no internal id exposure" rule end-to-end.
- **HS256** signing via `jjwt` (issuing) + Spring `NimbusJwtDecoder` (validation). Both share the `JWT_SECRET`. `JwtProperties` is `@Validated` with `@Size(min = 32)` so app refuses to boot with a short/missing secret.
- **Stateless security filter chain**, CSRF disabled (no session), `POST /api/v1/auth/login` is `permitAll`, everything else authenticated. Method-level `@EnableMethodSecurity` for `@PreAuthorize` in later phases.
- **BCrypt** password hashing throughout — used by `AuthenticationManager` + `AdminSeeder` + `AuthService.changePassword`.
- **`AdminSeeder`** is a `CommandLineRunner`: idempotent (skips if any user exists), validates required config non-blank when enabled, seeds `User` + `Credentials` + `UserRole(ADMIN)` in one transaction.
- **Global RFC-7807-style error responses** (`{ timestamp, status, error, message, path, fieldErrors? }`) via `@RestControllerAdvice`. `SecurityConfig` uses the same JSON shape for auth failures (401/403).
- **Profile module** — `GET /api/v1/users/me` and `PATCH /api/v1/users/me`. Structural authorization: only `/me` exists, so users can never target another user's profile via this endpoint. `PATCH` uses null-means-"don't-change" semantics; when a field is present it's `@Valid`-ated. Email change triggers a uniqueness check → `CONFLICT_DUPLICATE_EMAIL` (409) on collision. Response uses natural keys (`username`, `email`); internal `id` never exposed.
- **Agent + Customer management** — `POST/GET /api/v1/agents` (ADMIN only via `@PreAuthorize("hasRole('ADMIN')")` on the controller); `POST /api/v1/customers` (AGENT only), `GET /api/v1/customers` (AGENT sees own, ADMIN sees all — service branches on `currentUserService.hasRole(...)`). Creation writes User + Credentials + UserRole (+ Customer for the customer flow) in one transaction; pre-checks return 409 `CONFLICT_DUPLICATE_USERNAME` / `CONFLICT_DUPLICATE_EMAIL`, with the DB constraint + `DataIntegrityViolationException` handler as belt-and-suspenders for races. Response DTOs use natural keys throughout; customer response embeds `agent: {username, fullName}` with no ids.
- **Ticket management** — `POST /api/v1/tickets` (CUSTOMER only via `@PreAuthorize("hasRole('CUSTOMER')")`); `GET /api/v1/tickets` and `GET /api/v1/tickets/{id}` open to any authenticated caller, **scoped in the service** by role. URL `{id}` is the ticket's UUID `external_id` — internal `BIGINT` never exposed. Cross-user access on `GET /tickets/{id}` returns **404 `RESOURCE_NOT_FOUND`** rather than 403 (no info leak about existence). Filters: `status`, `from`, `to` via query params (ISO-8601 timestamps for the date range), composed with JPA `Specification`s built from `TicketSpecs`. Ordering: `createdAt DESC` on list.
