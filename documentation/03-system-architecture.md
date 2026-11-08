# System architecture

## Architecture style

Start with a **modular monolith**: one Spring Boot deployment and one PostgreSQL database, with explicit domain modules and transaction boundaries. This minimizes ₹0 operational cost and avoids premature distributed transactions. Modules should be extractable only after measured scaling, ownership, or availability needs justify the operational and consistency costs.

```text
Passenger browser / Admin browser
          | HTTPS
          v
Static Next.js application (Cloudflare Pages candidate)
          | versioned REST API
          v
Spring Boot modular monolith (Render free: prototype only)
          | JDBC / Flyway
          v
PostgreSQL (Supabase free candidate; not production-grade)
          | transactional outbox / job tables
          +--> database-backed worker loop (same deployable initially)
          +--> optional Redis cache / rate-limit acceleration
          +--> authorized payment provider adapter (later; sandbox first)
```

See [overall system](diagrams/system-context.mmd), [deployment](diagrams/deployment.mmd), and [event flow](diagrams/event-flow.mmd).

## Module boundaries

| Module | Owns | May depend on |
|---|---|---|
| identity-access | Users, credentials, sessions, MFA, roles, permission checks | shared kernel |
| railway-catalog | Stations, train definitions, versioned routes/stops/schedules, coach layouts/compositions (Phase 3 master data) | identity-access permissions, shared kernel |
| operations | Dated runs, operational status, disruption, cancellation, announcements | railway-catalog |
| inventory | Run inventory, coaches, seats, holds, allocations, quotas, waitlist/RAC | railway-catalog, operations |
| fares-rules | Versioned fares, eligibility, quota and cancellation rule evaluation | railway-catalog |
| reservations | Booking aggregate, passengers, journey segments, lifecycle, ticket/PNR issuance | inventory, fares-rules |
| payments | Payment attempts, verified provider events, ledger, refunds, reconciliation | reservations (contract only) |
| support | Cases and scoped support actions | identity-access, reservations (read/command contracts) |
| notifications | Outbox consumers, delivery attempts, templates and preferences | module events |
| reporting-audit | Audit records, operational projections and reporting | read-only projections |

Rules: modules expose application-service contracts/events; no cross-module table writes; avoid cyclic dependencies; shared kernel is small and contains value types only. One transaction may coordinate reservation/inventory/payment intent where required, but external provider calls never occur inside a DB transaction.

The Phase 3 implementation remains within the existing Spring Boot modular monolith and PostgreSQL/Flyway deployment. Staff master-data APIs are protected by permissions and write audit/version records transactionally. The operations portal is a separate frontend workspace; the responsive master-data screens currently live in the authenticated admin portal.

Phase 6 adds a train-run inventory module to the same API. PostgreSQL stores immutable dated route/composition snapshots and segment claims; a unique `(inventory_berth_id, segment_index)` key prevents overlapping holds or operational blocks from claiming the same physical berth. Passenger availability and hold controls are available on both device-specific journey details experiences. Staff inventory administration and operations tools remain permission-gated; the inventory feature does not implement confirmed reservations or payments. See [Phase 6 seat inventory](phase-6-seat-inventory.md).

## Technology choices and tradeoffs

- **Next.js / React / TypeScript / Tailwind:** typed, accessible, reusable UI. Prefer static export and API-driven pages for lowest-cost static hosting. Any SSR/edge runtime feature must be proven compatible with the selected free host before use; API remains independent.
- **Java / Spring Boot / Spring Security:** familiar transaction, security, validation, and ecosystem support. Memory footprint and cold start require measurement on free compute.
- **PostgreSQL / Flyway:** relational integrity, exclusion constraints, row locks, transactional outbox, and repeatable migrations suit reservation correctness. JPA for normal CRUD; explicit SQL for critical allocation and reporting.
- **Redis:** optional, only for ephemeral cache or best-effort rate-limit acceleration. Never the owner of holds, idempotency, payment status, or authoritative availability.
- **Search:** PostgreSQL indexes and full-text search initially; OpenSearch only after corpus/query scale justifies cost/operations and a zero-cost offering is verified.
- **Messaging:** outbox plus durable PostgreSQL job processing at first; Kafka is a future option, not part of the zero-cost deployment baseline.
- **Docker / GitHub Actions:** reproducible local and CI builds. Hosted CI minutes/storage limits depend on repository/account plan; disable unbounded jobs and monitor usage.
- **Kubernetes / Prometheus / Grafana / OpenTelemetry:** portability and future observability options, not initial deployment requirements. Local Compose and exportable telemetry first; hosted services must remain free and approved.

## Synchronous and asynchronous boundaries

Synchronous API handles authentication, catalogue reads, quote calculation, reservation requests, cancellation request, and admin commands. In one local DB transaction, reserve inventory and persist reservation/payment intent/outbox records. Notifications, search indexing, analytics, provider refund calls, and reconciliation run asynchronously from durable records and are idempotent.

Do not make confirmation depend on a browser redirect or one webhook delivery. On provider timeout, keep payment/reservation in explicit pending state; query/reconcile provider status before releasing or confirming inventory according to approved policy.

## Consistency rules

Strong consistency: physical seat overlap, quota capacity, reservation transitions, idempotency records, payment/refund ledger, role changes, and approved operational mutations.

Eventual consistency: catalogue search projection, email/SMS delivery, dashboards, aggregated reports, and cache invalidation. Every projection has a source event / watermark and freshness metadata.

## Failure containment

- API cannot reach DB: readiness fails and booking commands return a typed temporary error; no memory fallback.
- Cache unavailable: bypass cache where safe, increase DB protection/rate limits; never claim stale cache as authoritative free inventory.
- Provider callback delayed: retain `PENDING`/`UNKNOWN`, poll/reconcile idempotently; do not confirm solely on client return.
- Worker restart: reclaim expired leases and process durable outbox/jobs; uniqueness and idempotency prevent duplicate effects.
- Provider free service sleeps or pauses: show service unavailable / retry guidance and preserve records already committed. There is no guaranteed continuous service on free tiers.

## API and deployment boundaries

Use `/api/v1` REST with OpenAPI contract; JSON errors follow RFC 9457-style `application/problem+json` plus stable application code. Admin routes are separate by authorization namespace, not a weaker separate trust system. See [API architecture](06-api-architecture.md).

The proposed local environment uses Docker Compose for PostgreSQL and app dependencies. Candidate free-tier deployment is suitable only for disposable demonstration; see [infrastructure](11-infrastructure-and-devops.md), [deployment](13-deployment-strategy.md), and [cost audit](zero-cost-infrastructure.md).
