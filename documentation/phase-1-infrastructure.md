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

**Phase 1 acceptance checklist is complete.** The validation evidence and limitations are recorded below.

### Acceptance checklist (2026-11-05)

- [x] Frontend builds without errors (all three apps; lint and strict TypeScript checks also pass).
- [x] Backend builds without errors (Java 17 unit tests and JAR packaging passed).
- [x] PostgreSQL connection verified — Flyway connected to Supabase PostgreSQL 17.11 over TLS.
- [x] Flyway migration successfully executed — V1 ran in the dedicated `railconnect` schema; `mvn verify` confirmed the metadata table and Flyway history.
- [x] Docker Compose starts successfully — GitHub Actions built and started the full Compose stack; API readiness/liveness and OpenAPI/Swagger probes passed; CI shut the stack down afterward.
- [x] Health endpoints return correct responses — readiness and liveness returned HTTP 200 / `UP` with Flyway enabled.
- [x] API documentation works — `/v3/api-docs` returned HTTP 200 with OpenAPI 3.1.0 and `/swagger-ui.html` returned HTTP 200.
- [x] GitHub Actions workflows pass — backend, frontend, and infrastructure workflows all succeeded on `main`.
- [x] No high-confidence secrets detected in repository history — all seven reachable commits before this final checklist-only commit were scanned; `.env` is ignored and is not tracked. Pattern scanning does not guarantee detection of every possible secret format.
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

- Scanning outside the seven scanned Git commits or detecting unrecognized/obfuscated secret formats.

The configured Supabase project connection succeeded over TLS. After isolating application data in the `railconnect` schema, `mvn verify` passed: six unit tests and four integration tests, including PostgreSQL connectivity, Flyway migration, API health, and authorization checks. Flyway applied V1 without baselining or changing the existing `public` schema. Backend and frontend GitHub Actions runs passed on commit `21cd0f2`; the infrastructure workflow passed on commit `cc92f98`, including full Compose startup, API health/OpenAPI probes, and teardown. Seven reachable commits before this final documentation-only commit were scanned for high-confidence secret patterns with no matches; `.env` remains ignored and untracked. No paid infrastructure was provisioned.

## Phase boundary

Phase 1 is complete. Phase 2 identity/RBAC implementation was explicitly approved and is documented in the Phase 2 guides. Its database-backed verification is still pending; do not proceed to Phase 3. Do not load real passenger data, add fake availability, enable payment, or deploy a production service.
