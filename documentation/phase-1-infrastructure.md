## Scope and guardrails

Phase 1 was explicitly authorized after Phase 0. This phase establishes buildable application foundations only. It does not implement search, reservations, seat inventory, payment flows, railway operations, production authentication, or an external railway integration. All app pages state that their operational functions are not active.

The passenger UI has two distinct webpages: `/mobile` and `/laptop`. The root route selects by a 760 px viewport breakpoint, while accessible links allow manual switching. Both pages use shared components, API utility, and design tokens. Admin and operations are separate Next.js workspaces.

## Implemented foundation

- npm workspaces: three standalone Next.js apps and the shared `@railconnect/ui` component package.
- Strict TypeScript configuration, Tailwind design tokens, accessible typography/focus/error patterns, reusable button/card/input/dialog/loading/navigation components.
- Passenger loading/error/not-found states and device-aware route selector.
- Separate admin and operations starter pages with no mock records or enabled actions.
- Spring Boot 3.5 / Java 17 service with Spring Security stateless defaults, validation, JPA, Flyway, PostgreSQL, optional Redis, Actuator, Prometheus registry, and springdoc.
- Standard JSON response/error envelope, request IDs, 1 MiB request-body limit (including streamed bodies), security response handlers, health/readiness/liveness, system info, CORS allowlist, structured logs.
- One minimal Flyway metadata table; no railway-domain schema.
- Docker Compose with persistent local PostgreSQL, optional Redis profile, non-root runtime containers, health checks, and localhost-only published ports.
- CI validation workflows; no deployment workflow and no hosted service provisioning.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/health` | Readiness summary |
| GET | `/api/v1/health/readiness` | Readiness including PostgreSQL |
| GET | `/api/v1/health/liveness` | Process liveness, independent of PostgreSQL |
| GET | `/api/v1/system/info` | Minimal public application/API identity |
| GET | `/swagger-ui.html` | Local OpenAPI UI |
| GET | `/v3/api-docs` | OpenAPI document |

Error responses include `success`, stable `error.code/message/details`, ISO timestamp, and `requestId`. Sensitive runtime internals are not included.

## Repository structure

See the root [README](../README.md) for a concise tree and commands. The backend remains a single modular-monolith application. Passenger/admin/operations are separated by workspace and deployable static output while sharing only UI primitives.

## Validation status

**Implementation is delivered; remaining infrastructure and repository-history checks are listed below.**

### Acceptance checklist (2026-11-05)

- [x] Frontend builds without errors (all three apps; lint and strict TypeScript checks also pass).
- [x] Backend builds without errors (Java 17 unit tests and JAR packaging passed).
- [x] PostgreSQL connection verified — Flyway connected to Supabase PostgreSQL 17.11 over TLS.
- [x] Flyway migration successfully executed — V1 ran in the dedicated `railconnect` schema; `mvn verify` confirmed the metadata table and Flyway history.
- [ ] Docker Compose starts successfully — Docker/Compose unavailable.
- [x] Health endpoints return correct responses — readiness and liveness returned HTTP 200 / `UP` with Flyway enabled.
- [x] API documentation works — `/v3/api-docs` returned HTTP 200 with OpenAPI 3.1.0 and `/swagger-ui.html` returned HTTP 200.
- [ ] GitHub Actions workflows pass — the repository has no remote branches/workflow runs yet; push was denied because the authenticated GitHub account lacks write access.
- [x] No secrets committed in the current repository history — the initial 135-file commit passed a credential-pattern scan; local `.env` is ignored and was not staged. This does not establish anything about secrets in external systems or prior GitHub history.
- [x] No paid infrastructure activated — no hosted service was provisioned.
- [x] All Phase 1 documentation created.

Verified locally:

- All three frontend applications pass lint, strict TypeScript checks, and static production builds.
- Passenger `/mobile` and `/laptop` pages each returned HTTP 200; browser checks confirmed the mobile bottom navigation, narrow-screen single-column layout, no horizontal overflow at 390 px, and the laptop two-column layout at 1440 px.
- Six backend unit tests pass on Java 17; the backend JAR packages successfully.
- `mvn verify` against Supabase passed four integration tests, including the PostgreSQL connection, Flyway migration, API health, and authorization checks.
- Frontend tests pass (6 cases), and `npm audit --audit-level=high` reports zero vulnerabilities.
- YAML files parse, the POM parses, documentation links resolve, and static exports for all app routes exist.

Not verified:

- Docker Compose validation/builds.
- Execution of GitHub Actions workflows: the remote `chaithrakamala30-sys/IRCTC` was empty at inspection, and GitHub rejected the push with HTTP 403 (`Permission denied` to the authenticated account).

The configured Supabase project connection succeeded over TLS. After isolating application data in the `railconnect` schema, `mvn verify` passed: six unit tests and four integration tests, including PostgreSQL connectivity, Flyway migration, API health, and authorization checks. Flyway applied V1 without baselining or changing the existing `public` schema. A runtime launch with Flyway enabled also confirmed readiness/liveness and Swagger/OpenAPI. The project was committed locally as `a417338`; a credential-pattern scan found no high-confidence secrets in staged source, and `.env` remained ignored. CI is configured to run backend integration tests against ephemeral PostgreSQL and validate/build Docker images, but those workflows have not been executed because the GitHub push was denied. Close the remaining acceptance gates with Docker/Compose and a successful push by an authorized account.

## Phase boundary

Stop after Phase 1. Phase 2 identity/RBAC implementation requires explicit approval. Do not load real passenger data, add fake availability, enable payment, or deploy a production service.
