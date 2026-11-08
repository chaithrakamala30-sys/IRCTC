# Phase 3 — Railway master data

## Status and boundaries

Phase 3 adds the first authenticated railway administration APIs and admin-portal workspace for reference data. It does not enable passenger train search, dated train runs, seat availability, booking, reservations, payment, or live railway-system integrations. No railway data is seeded; authorized staff must supply and validate the records.

The change is append-only at the database level: `V5__create_railway_master_data.sql` adds the schema without editing V1–V4. The migration is intended for PostgreSQL 16+ and is run by Flyway. Hosted database changes require explicit approval and a restorable backup.

## Data model

V5 adds:

| Area | Tables | History and lifecycle |
|---|---|---|
| Geography and stations | `railway_zones`, `railway_divisions`, `stations`, `station_facilities`, `station_versions` | Station codes are unique. New stations are drafts and require an explicit code-verification action before activation. Each create, update, verification, or status change adds an immutable station snapshot version. |
| Train catalogue | `train_categories`, `train_types`, `trains` | Train numbers are unique. Train records are soft-status controlled; activation requires an effective published route. |
| Routes | `train_routes`, `train_route_versions`, `train_route_stops` | Route versions have immutable ordered stop snapshots and draft/published/superseded/retired states. Publishing supersedes the former published version without deleting it. |
| Schedules | `train_schedules`, `train_schedule_days`, `train_schedule_exceptions` | Each write creates a schedule version. Weekdays and dated RUN/CANCEL exceptions are stored separately. Only a published route version can be scheduled. |
| Coach templates and units | `coach_classes`, `coach_types`, `coach_configurations`, `coaches`, `accommodation_layouts`, `physical_accommodations` | Layouts are versioned and published separately. Capacity must equal active physical accommodations. Positions are JSON objects for layout-specific coordinates. |
| Composition | `train_compositions`, `train_composition_versions`, `train_composition_coaches` | Each saved composition is a draft snapshot; publishing updates the composition’s current-version pointer and preserves the previous version. |
| Audit | `master_data_audit_logs` | Changes and publish actions are recorded in the same transaction as the data mutation, with actor and request correlation ID. |

Important constraints are enforced both at the database and service boundaries: distinct train/route endpoints, unique stop sequence and station within a route version, nonnegative day offsets and halt, ordered times and distances, effective-date ordering, unique schedule weekdays and exceptions, valid accommodation types, unique coach positions/numbers, and unique composition positions/numbers.

## API reference

All paths below are under `/api/v1/admin/master-data`. Responses use the existing `ApiEnvelope` contract. Every route requires an authenticated staff session and its named permission; mutations also require recent authentication. Publish operations require the separate publish permission.

| Method and path | Permission | Purpose |
|---|---|---|
| `GET /dashboard` | `operations:master-data:read` | Counts for stations, trains, published routes, coach classes, and coach units. |
| `GET /stations` / `GET /stations/{id}` | `operations:master-data:read` | Bounded page, search/filter, and station details. |
| `POST /stations`, `PUT /stations/{id}` | `operations:stations:manage` | Create or update a station and its facility set. |
| `PATCH /stations/{id}/verification`, `PATCH /stations/{id}/status` | `operations:stations:manage` | Record code verification and activate/deactivate/retire. |
| `GET /stations/{id}/versions` | `operations:master-data:read` | Append-only station snapshots. |
| `GET/POST /categories`, `GET/POST /train-types` | Read: `operations:master-data:read`; write: `operations:trains:manage` | Read and add train classification lookups. |
| `GET /trains`, `GET /trains/{id}` | `operations:master-data:read` | Bounded train directory and details. |
| `POST /trains`, `PUT /trains/{id}`, `PATCH /trains/{id}/status` | `operations:trains:manage` | Create/update train definitions and manage lifecycle. |
| `GET /routes`, `GET /route-versions/{id}` | `operations:master-data:read` | Route version directory/details. |
| `POST /routes`, `POST /routes/{id}/versions` | `operations:routes:manage` | Create route and draft versions with ordered stops. |
| `POST /route-versions/{id}/publish` | `operations:master-data:publish` | Publish a validated route version. |
| `GET /schedules`, `GET /schedules/{id}` | `operations:master-data:read` | Schedule versions and details. |
| `POST /schedules` | `operations:schedules:manage` | Add a draft schedule version, weekdays, and dated exceptions. |
| `POST /schedules/{id}/publish` | `operations:master-data:publish` | Publish a schedule after route publication. |
| `GET/POST /coach-classes`, `GET/POST /coach-types` | Read: `operations:master-data:read`; write: `operations:composition:manage` | Coach class/type catalogue. |
| `GET/POST /coach-configurations`, `GET /coach-configurations/{id}` | Read: `operations:master-data:read`; write: `operations:composition:manage` | Configuration and initial layout draft. |
| `POST /coach-configurations/{id}/layouts` | `operations:composition:manage` | Add a layout version. |
| `POST /coach-configurations/{id}/layouts/publish` | `operations:master-data:publish` | Publish the latest draft layout. |
| `GET/POST /coaches` | Read: `operations:master-data:read`; write: `operations:composition:manage` | Register physical coach units tied to published layouts. |
| `GET/PUT /trains/{id}/composition` | Read: `operations:master-data:read`; write: `operations:composition:manage` | Read or save a draft composition version. |
| `POST /trains/{id}/composition/versions/{versionId}/publish` | `operations:master-data:publish` | Publish the validated composition version. |
| `GET /audit` | `operations:master-data:audit:read` | Filtered, bounded master-data audit history. |

## Permissions

V5 inserts the new permission codes and grants them to `SUPER_ADMIN` and `RAILWAY_ADMIN` idempotently. `STATION_MANAGER` does not receive global master-data privileges by default. Authorization is enforced on the API using Spring method security; frontend navigation and role visibility are not security controls.

## Admin interface

The separate Next.js admin portal includes responsive overview, station, train, route, schedule, and coach/composition sections. It supports station and train editing, station verification/history, route stop entry and version creation, schedule weekdays/exceptions/version creation, coach class/type/configuration/layout setup, coach units, composition ordering, draft/publish actions, and audit history. Data loads from the authenticated API; there are no mock counts or seeded railway records. The same interface adapts from laptop width to narrow mobile browsers.

## Validation and known limitations

- Database writes and their station-version/audit records are transactional. SQL constraints remain the final integrity barrier.
- Data is not an authoritative Indian Railways feed. Staff-entered data needs a verified source and operational review.
- There is no multi-person approval workflow, bulk import, map integration, schedule-to-dated-run generator, inventory, seat availability, search, booking, payment, or passenger-facing timetable.
- MFA and password recovery remain unavailable as documented in Phase 2.
- Flyway V5 has been applied and verified on Supabase; see the verification record below. Application integration tests still require a successful PostgreSQL-backed CI run against an isolated test database. An unavailable local Docker/PostgreSQL service means those local integration tests remain **unverified locally**.
- No paid infrastructure was added or activated.

## Verification record

Latest verification:

| Check | Result | Evidence |
|---|---|---|
| Frontend lint, type-check, and production builds | PASS | `npm run check:frontend` completed with exit code 0 for admin, operations, and passenger apps. |
| Backend compilation, unit tests, packaging | PASS | `mvn verify` completed with exit code 0; 16 unit tests passed, with 0 failures/errors. |
| Supabase Flyway V5 migration | PASS | Applied 2026-11-08 to the configured `railconnect` schema on PostgreSQL 17.11 after confirming V1–V4 were successful and that a restorable backup was ready. Flyway recorded V5 as successful. |
| Supabase schema and permission verification | PASS | Confirmed all 24 V5 tables, all 8 V5 permissions, and all 8 grants each for `SUPER_ADMIN` and `RAILWAY_ADMIN`. |
| PostgreSQL migration/API/identity/master-data integration tests | UNVERIFIED | Earlier local Failsafe run discovered 10 integration tests and skipped all 10 because `RAILCONNECT_DB_URL` was not set in that run. The database-backed application flow test has not been run against the production database. |
| Docker-backed local database availability | BLOCKED | Docker CLI is not installed in the local environment. |
| Browser E2E | UNVERIFIED | Playwright is not installed (`npx --no-install playwright --version` could not resolve the package). |

The migration has been applied to Supabase, but application integration tests still need to run against an isolated PostgreSQL test database in CI. Do not run mutation-oriented integration tests against production.
