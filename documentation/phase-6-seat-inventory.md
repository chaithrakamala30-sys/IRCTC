# Phase 6 — Train-run seat inventory

Phase 6 introduces dated, train-run-specific physical seat inventory. PostgreSQL is the source of truth for run snapshots, availability, holds, operational blocks, and inventory audit events. The feature is implemented in the existing Spring Boot modular monolith and is surfaced in passenger, administration, and operations web applications.

## Scope and exclusions

The inventory service can initialize a dated run from currently published railway master data, report availability for an origin/destination segment, list available physical berths, create and release short-lived passenger-owned holds, and let authorized staff block or release a berth for an operational reason.

A hold is only a temporary inventory lock. It is not a reservation, confirmed seat allocation, ticket, PNR, payment, or booking guarantee. Reservation allocation, RAC/waitlist, payments, ticketing, cancellations/refunds, and chart preparation are explicitly outside this phase.

No sample trains or inventory are seeded. A run appears only after an authorized administrator initializes a date for a train with applicable published schedule, route, composition, and physical layout data.

## Run snapshot lifecycle

`POST /api/v1/admin/inventory/runs` accepts a train ID and service date. Initialization verifies that the train is active, a published schedule and route cover the requested date and operating day (including an explicit `RUN` schedule exception), and a published composition is available. It snapshots route stops and segment distances, coach configuration and class information, and active physical accommodations. Initialization is transactional and idempotent for a train/date pair; a later master-data change does not rewrite an existing run snapshot.

The date filter uses the schedule's local operating date. Run segments are represented by zero-based adjacent segments; a journey from stop index `i` to stop index `j` occupies `[i, j)`. Requests for a station not on the run or with origin at/after destination are rejected.

## Availability and claim integrity

Availability is calculated for the exact run and requested segment, with class totals split into available, held, blocked, and unavailable berths. A berth is available only if every segment in the requested interval is unclaimed and its base status is `AVAILABLE`.

The database primary key on `(inventory_berth_id, segment_index)` arbitrates competing holds and operational blocks. Claim rows also reference the corresponding run segment and source hold/block segment. Transactions create the hold/block and its segment rows and claims atomically. Per-run transaction advisory locks serialize claim selection, while the unique key remains the final concurrency guard. Therefore, adjacent non-overlapping journeys can use the same physical berth, while overlapping journeys cannot.

## Hold lifecycle

- Hold creation requires an authenticated passenger, coach class, journey endpoints, and an `Idempotency-Key`.
- The request fingerprint is stored with the passenger/key pair. A retry with the same payload returns the original hold; reuse of the key for a different payload is rejected.
- Holds expire ten minutes after creation. Expired claims are removed and hold status/audit are updated transactionally by request-time cleanup and a scheduled cleanup sweep.
- A passenger can list their own holds and release their own hold. Another passenger cannot read or release it.
- The default maximum is four active holds per passenger. A per-passenger request-rate guard limits hold requests.
- No payment or booking transition exists in Phase 6.

## Operations and audit

The Operations Manager, Station Manager, Railway Admin, and Super Admin permissions in Flyway V9 separate read, initialization, and block-management actions. Passenger hold endpoints additionally require the Passenger role and `inventory:holds:manage`. The operations portal provides staff sign-in, dated-run initialization for permitted roles, run selection, journey-segment selection, berth blocking/release, and audit history. The admin portal provides a dedicated inventory tab. Inventory audit displays actor names (system-generated expiration events are identified as `System`); raw actor UUIDs are not required in the UI.

Inventory audit rows are append-only. Run snapshot content cannot be updated or deleted; the only supported run-row update is the narrow `READY` to `CANCELLED` status transition, which preserves all snapshot data.

## API summary

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/api/v1/public/inventory/runs` | Public | List initialized runs; optional date/train filters |
| GET | `/api/v1/public/inventory/runs/{runId}` | Public | Read run snapshot route |
| GET | `/api/v1/public/inventory/availability` | Public | Class availability for a selected segment |
| GET | `/api/v1/public/inventory/berths` | Public | Paginated available physical berths |
| POST | `/api/v1/passenger/inventory/holds` | Passenger | Create an idempotent temporary hold |
| GET | `/api/v1/passenger/inventory/holds` | Passenger | List the signed-in passenger's holds |
| DELETE | `/api/v1/passenger/inventory/holds/{holdId}` | Passenger | Release an owned hold |
| GET | `/api/v1/admin/inventory/trains` | Inventory initializer | List configured trains eligible for run initialization |
| GET | `/api/v1/admin/inventory/runs` | Inventory reader | List initialized runs |
| POST | `/api/v1/admin/inventory/runs` | Inventory initializer | Initialize a dated run snapshot |
| GET | `/api/v1/admin/inventory/runs/{runId}/blocks` | Inventory reader | List operational blocks |
| POST | `/api/v1/admin/inventory/runs/{runId}/blocks` | Block manager | Block a berth on a segment |
| DELETE | `/api/v1/admin/inventory/blocks/{blockId}` | Block manager | Release a block |
| GET | `/api/v1/admin/inventory/runs/{runId}/audit` | Inventory reader | Read append-only run audit |

All responses use the standard API envelope. Public availability is advisory and can change immediately; only the backend hold transaction arbitrates inventory claims.

## Database

Flyway V9 adds the inventory schema, indexes, constraints, expiry support, permissions, and the Operations Manager role. See [database design](05-database-design.md) for the phase inventory overview and [Phase 6 test report](phase-6-testing-report.md) for what has and has not been validated.
