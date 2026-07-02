# Progress

## Current status
Phase 0 — done. Starting Phase 1.

## Phase checklist
- [x] Phase 0 — Scaffolding
- [ ] Phase 1 — Docker
- [ ] Phase 2 — Schema & entities
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
