# Customer Support Hub

A REST backend for managing customer support interactions. Three roles — **ADMIN**, **AGENT**, **CUSTOMER** — with role-scoped access to profiles and tickets. Built with Spring Boot 3.x, Spring Data JPA, MySQL, JWT authentication (HS256), and Docker.

## Tech stack

- Java 21, Gradle (Kotlin DSL)
- Spring Boot 3.5.x (Web, Data JPA, Validation, Security, OAuth2 Resource Server)
- MySQL 8.4 + Flyway
- JWT (HS256) via `jjwt` for issuing and `NimbusJwtDecoder` for validation
- Docker + Docker Compose

## Quick start

Requires Docker Desktop running.

```
cp .env.example .env
docker compose up --build
```

`.env.example` is the committed template with dev-safe defaults. `.env` is gitignored — edit it locally to change database credentials, the JWT signing secret, or the seeded admin's password. Docker Compose refuses to start if any required secret (`MYSQL_*_PASSWORD`, `JWT_SECRET`) is missing.

Services:
- `mysql` on host port `3306`, persistent volume `mysql-data`.
- `app` on host port `8080`, waits for MySQL healthcheck before booting.

On first boot the app seeds a single ADMIN user from `.env`:

```
ADMIN_SEED_USERNAME=admin
ADMIN_SEED_PASSWORD=admin_dev_pw   # change before first boot
```

Tear down:

```
docker compose down       # keep the database
docker compose down -v    # wipe the database too
```

Local run without Docker requires MySQL on `localhost:3306` and all secrets in the shell environment — no in-source defaults:

```
DB_URL="jdbc:mysql://localhost:3306/customer_hub" DB_USERNAME=root DB_PASSWORD=root \
JWT_SECRET="a-very-long-development-only-secret-32-chars-min" \
JWT_ISSUER=customer-support-hub JWT_TTL_MINUTES=60 \
./gradlew bootRun
```

Build the deployable jar:

```
./gradlew clean bootJar
```

Produces `build/libs/customer-support-hub-0.0.1-SNAPSHOT.jar`.

## Role model

| Capability                                     | ADMIN | AGENT | CUSTOMER |
|------------------------------------------------|:-----:|:-----:|:--------:|
| Log in / change own password                   |   ✅  |   ✅  |    ✅    |
| Read / update own profile (`/users/me`)        |   ✅  |   ✅  |    ✅    |
| Create an agent                                |   ✅  |       |          |
| List all agents                                |   ✅  |       |          |
| Create a customer (registered under the agent) |       |   ✅  |          |
| List customers                                 |   ✅  |   ✅¹ |          |
| Create a ticket                                |       |       |    ✅    |
| List / search tickets                          |   ✅² |   ✅³ |    ✅⁴   |
| Get a specific ticket by UUID                  |   ✅  |   ✅³ |    ✅⁴   |

¹ AGENT sees only their own customers. ADMIN sees all.
² ADMIN sees all tickets.
³ AGENT sees tickets whose customer they own.
⁴ CUSTOMER sees only their own tickets. Cross-user access on `GET /tickets/{id}` returns 404 `RESOURCE_NOT_FOUND` (not 403 — avoids leaking that the resource exists).

## Endpoint reference

All paths are prefixed with `/api/v1`. All authenticated endpoints expect an `Authorization: Bearer <token>` header. All request/response bodies are JSON.

| Method | Path                | Auth      | Role gate                        | Success | Description                                                                            |
|:------:|---------------------|-----------|----------------------------------|:-------:|----------------------------------------------------------------------------------------|
| POST   | `/auth/login`       | none      | —                                | 200     | Exchange username + password for a JWT.                                                |
| POST   | `/auth/password`    | JWT       | any                              | 204     | Change own password (requires current password).                                       |
| GET    | `/users/me`         | JWT       | any                              | 200     | Read own profile.                                                                      |
| PATCH  | `/users/me`         | JWT       | any                              | 200     | Update own profile (`fullName`, `email`). Null fields mean "don't change".             |
| POST   | `/agents`           | JWT       | ADMIN                            | 201     | Create an agent (user + credentials + role in one transaction).                        |
| GET    | `/agents`           | JWT       | ADMIN                            | 200     | List all agents.                                                                       |
| POST   | `/customers`        | JWT       | AGENT                            | 201     | Create a customer registered under the caller.                                         |
| GET    | `/customers`        | JWT       | AGENT or ADMIN                   | 200     | AGENT: own customers. ADMIN: all.                                                      |
| POST   | `/tickets`          | JWT       | CUSTOMER                         | 201     | Create a ticket owned by the caller.                                                   |
| GET    | `/tickets`          | JWT       | any (service-scoped by role)     | 200     | Filter by `?status`, `?from`, `?to` (ISO-8601 timestamps). Ordered by `createdAt DESC`.|
| GET    | `/tickets/{id}`     | JWT       | any (service-scoped by role)     | 200     | `{id}` is the ticket's UUID `external_id`. Cross-user access → 404.                    |

### Request / response shapes

```jsonc
// POST /auth/login  →  200
// { "username": "...", "password": "..." }
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600 }

// GET /users/me  →  200
{ "username": "...", "email": "...", "fullName": "..." }

// PATCH /users/me
// { "fullName": "...", "email": "..." }   // both optional (null = don't change)

// POST /agents  |  POST /customers
{
  "username": "3-64 chars, [a-zA-Z0-9._-]",
  "password": "8-100 chars",
  "email":    "valid email, ≤255 chars",
  "fullName": "≤255 chars"
}
// Response (agent):   { "username", "email", "fullName" }
// Response (customer): { "username", "email", "fullName", "agent": { "username", "fullName" } }

// POST /tickets
// { "title": "≤255 chars", "description": "≤4000 chars" }
// Response:
{
  "id": "550e8400-e29b-41d4-a716-446655440000",   // UUID, NOT the DB id
  "title": "...", "description": "...",
  "status": "OPEN | IN_PROGRESS | RESOLVED | CLOSED",
  "createdAt": "2026-07-04T12:34:56Z",
  "updatedAt": "2026-07-04T12:34:56Z",
  "customer": { "username": "...", "fullName": "..." }
}
```

### Error shape

Every failure returns the same JSON body from `GlobalExceptionHandler` and `SecurityConfig`:

```jsonc
{
  "timestamp": "2026-07-04T12:34:56Z",
  "status":    400,
  "error":     "Bad Request",
  "code":      "INVALID_PARAMETER",   // machine-readable; branch on this
  "message":   "A request parameter has an invalid value.",
  "path":      "/api/v1/tickets",
  "fieldErrors": [                    // present only on validation errors
    { "field": "status", "message": "'status' must be one of: OPEN, IN_PROGRESS, RESOLVED, CLOSED." }
  ]
}
```

Codes actually returned by the service: `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `PASSWORD_CURRENT_INCORRECT`, `PASSWORD_SAME_AS_CURRENT`, `AUTH_MISSING`, `AUTH_INVALID_CREDENTIALS`, `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, `CONFLICT_DUPLICATE_USERNAME`, `CONFLICT_DUPLICATE_EMAIL`, `CONFLICT_GENERIC`, `INTERNAL_ERROR`.

## Curl walkthrough — golden path

```bash
# 1. Log in as the seeded admin
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin_dev_pw"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

# 2. ADMIN creates an AGENT
curl -X POST localhost:8080/api/v1/agents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"agent1","password":"agent_pw_123","email":"agent1@demo.local","fullName":"Agent One"}'

# 3. AGENT logs in, creates a CUSTOMER
AGENT_TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"agent1","password":"agent_pw_123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

curl -X POST localhost:8080/api/v1/customers \
  -H "Authorization: Bearer $AGENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"username":"cust1","password":"cust_pw_1234","email":"cust1@demo.local","fullName":"Customer One"}'

# 4. CUSTOMER logs in, creates a ticket
CUST_TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"cust1","password":"cust_pw_1234"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

curl -X POST localhost:8080/api/v1/tickets \
  -H "Authorization: Bearer $CUST_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Cannot login","description":"password reset link is broken"}'

# 5. AGENT lists tickets from their customers (service-scoped)
curl -H "Authorization: Bearer $AGENT_TOKEN" "localhost:8080/api/v1/tickets?status=OPEN"
```

## Design decisions worth calling out

### No internal id ever appears in a URL or response body

Two-ID pattern applied only where the API actually needs an external identifier:

- **Tickets** have `external_id BINARY(16)` (UUIDv4). `/tickets/{id}` uses that UUID. The internal `BIGINT` is DB-only.
- **Users** have no external UUID. All user-facing DTOs use natural keys (`username`, `email`).
- **Customers, credentials, user_roles** are never referenced externally — no UUID.
- **JWT `sub` = username**, not a user id. The server resolves `username → User` per request via `CurrentUserService`.

This is enforced across every DTO in the codebase, not spot-checked.

### `users` ↔ `credentials` split

`credentials` shares the `users` primary key via `@MapsId`. Rationale: separation of concerns and no accidental password-hash leakage through a User DTO — the hash lives on a different entity. Also a clean extension point for MFA and password rotation history.

### Layered role enforcement

Three layers, each doing one thing:

1. `@PreAuthorize("hasRole('X')")` on the controller — coarse gate.
2. Service-layer role branching (`currentUserService.hasRole(...)`) — fine gate for endpoints open to multiple roles.
3. Cross-user access on `GET /tickets/{id}` returns **404 `RESOURCE_NOT_FOUND`**, not 403 — 403 would leak that the ticket exists.

### Duplicate handling — belt and suspenders

Creating an agent or customer: pre-check in the service (unique `username`, unique `email`) → returns 409 `CONFLICT_DUPLICATE_USERNAME` / `CONFLICT_DUPLICATE_EMAIL`. Backed by DB unique constraints, with `DataIntegrityViolationException` mapped to the same 409 in `GlobalExceptionHandler` to handle the race.

### JWT hardening

`NimbusJwtDecoder` validates signature + `exp` by default; the app additionally validates `iss` via a `JwtClaimValidator` wired through `DelegatingOAuth2TokenValidator`. Tokens with a mismatched issuer are rejected with 401 `AUTH_MISSING`. The response body never says *why* the token was rejected — same generic message for expired, tampered, wrong-issuer, or missing.

### RFC-7807-style error responses

`GlobalExceptionHandler` (`@RestControllerAdvice`) handles: bean validation errors, malformed JSON, missing / mismatched request parameters, `IllegalArgumentException`, JPA integrity violations, custom domain exceptions, and a catch-all for the rest. `SecurityConfig` uses the same JSON shape for auth failures (401/403). Clients see one body shape across every failure mode.

## Testing

```
./gradlew test
```

86 tests across three layers:

- **Slice** — `CredentialsRepositoryTest`, `TicketRepositoryTest`, `UserRepositoryTest`. Real SQL through JPA, H2 in `MODE=MySQL` with Hibernate `create-drop`, Flyway disabled.
- **Service unit** — `AuthServiceTest`, `JwtServiceTest`, `ProfileServiceTest`, `AgentServiceTest`, `CustomerServiceTest`, `TicketServiceTest`. Mocked repositories.
- **Security integration** — `AuthSecurityIntegrationTest`, `ProfileIntegrationTest`, `RoleSecurityIntegrationTest`, `TicketSecurityIntegrationTest`. `@SpringBootTest` + `MockMvc` + real Spring Security filter chain + H2.

Security-aware tests cover: role-based access denial (403), unauthenticated access (401), cross-user access leakage (404 not 403), password change flows, JWT edge cases (expired, tampered signature, wrong issuer, missing roles claim), invalid enum on query params, and malformed UUID on path variables.

Why H2 instead of Testcontainers: speed and CI simplicity. Trade-off — MySQL-specific behavior like `BINARY(16)` UUID storage isn't exercised in tests. Verified manually via `curl` against the Docker container.

## Assignment → code mapping

| Assignment requirement                                                | Implementation                                                                                                                                              |
|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spring Boot 3.x + Spring Data JPA over MySQL + Spring Validation      | Spring Boot 3.5.x, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, MySQL 8.4                                                               |
| REST API via Spring MVC                                               | `@RestController` + `@RequestMapping` across `AuthController`, `ProfileController`, `AgentController`, `CustomerController`, `TicketController`             |
| Three role types: ADMIN, AGENT, CUSTOMER                              | `com.surense.customerhub.common.Role` enum, `user_roles` table, `@PreAuthorize` + service branching                                                         |
| AGENT creates customers, each registered under the AGENT              | `POST /customers` (`@PreAuthorize("hasRole('AGENT')")`), NOT NULL `customers.agent_id` FK, `CustomerService.createCustomer` sets `agent = currentUser`      |
| AGENT queries all their customers                                     | `GET /customers` (AGENT branch in `CustomerService.listCustomers`)                                                                                          |
| AGENT updates own profile                                             | `PATCH /users/me` (`ProfileController`)                                                                                                                     |
| CUSTOMER queries + updates own profile                                | `GET /users/me`, `PATCH /users/me` (same endpoints — role-agnostic, always own profile)                                                                     |
| ADMIN can do anything                                                 | `@PreAuthorize` grants ADMIN direct access to admin-only endpoints; service branching gives ADMIN unfiltered views on `GET /customers`, `GET /tickets`     |
| CUSTOMER creates tickets, gets own tickets                            | `POST /tickets` (`@PreAuthorize("hasRole('CUSTOMER')")`), `GET /tickets` (CUSTOMER branch in `TicketService.listTickets`)                                    |
| AGENT queries/searches tickets by their own customers                 | `GET /tickets` (AGENT branch in `TicketService.listTickets` via `TicketSpecs`), filters `?status`, `?from`, `?to`                                            |
| Spring OAuth for username/password auth                               | Spring OAuth2 Resource Server + JWT (HS256). Full authorization-server flow replaced with a `/auth/login` endpoint — clarified and approved with interviewer |
| Validate all incoming requests                                        | `@Valid` on every `@RequestBody`; `@Validated` on config properties; bean validation errors → 400 `VALIDATION_FAILED`                                        |
| HTTP statuses per error type (200, 400, 401, 403, 404, 409)           | All present — see the error-code list above                                                                                                                 |
| Human-readable error message                                          | `ErrorCode.defaultMessage()` on every error; contextual messages on validation and parameter errors                                                          |
| Unit tests for services                                               | `AuthServiceTest`, `JwtServiceTest`, `ProfileServiceTest`, `AgentServiceTest`, `CustomerServiceTest`, `TicketServiceTest` — 6 service unit test classes     |
| ≥1 security-aware unit test                                           | `AuthSecurityIntegrationTest`, `RoleSecurityIntegrationTest`, `TicketSecurityIntegrationTest` — 3 classes, dozens of security-aware assertions              |
| GitHub public repo                                                    | This repo                                                                                                                                                   |
| README                                                                | This file                                                                                                                                                   |
| Dockerfile                                                            | Multi-stage: JDK 21 builder → JRE 21 runtime, runs as non-root `app` user                                                                                    |
| docker-compose for local dev                                          | `docker-compose.yml` — MySQL 8.4 + app, `depends_on` waits on MySQL healthcheck, persistent `mysql-data` volume                                              |

## Repository layout

```
src/main/java/com/surense/customerhub/
├── auth/           # login, password change, JWT, security config, admin seeder
├── profile/        # /users/me
├── agent/          # /agents
├── customer/       # /customers
├── ticket/         # /tickets + Specification-based filtering
├── user/           # User entity + repository (shared by profile/agent/customer)
├── common/         # Role enum, shared exceptions
└── web/            # GlobalExceptionHandler, ErrorResponse

src/main/resources/db/migration/V1__init.sql   # Flyway schema
src/test/java/...                              # slice + service unit + security integration tests
```
