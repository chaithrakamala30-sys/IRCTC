# Scalability strategy

## Principles

1. Establish correctness before throughput; no optimization bypasses PostgreSQL reservation constraints.
2. Measure an actual baseline before scaling or claiming support for millions of users.
3. Keep the application stateless; critical state, idempotency, job leases, payment status, and inventory live in PostgreSQL.
4. Scale read and write paths separately only when profiling justifies it.
5. Free tiers are capacity/availability constraints, not evidence that the commercial target is achievable for ₹0.

## Workload and growth path

Start with the provisional normal/busy/flash-rush test profiles in [NFRs](02-non-functional-requirements.md). Instrument request rate, latency distributions, error rates, DB connection usage, query plans, lock wait, deadlocks, pool wait, outbox lag, hold age, cache hit, and provider timeout. Replay a realistic distribution with hot run/date/quota combinations; uniform random IDs hide contention.

### Application

- One modular monolith, stateless HTTP instances, bounded thread and connection pools, request timeouts, graceful shutdown, and readiness checks.
- Add replicas only after DB capacity, session strategy, job claiming, and deployment costs are understood. Ensure only one effective leader for schema migration and jobs use leases/`SKIP LOCKED`.
- Separate public search, booking, admin, and webhook rate limits; shed optional reporting/analytics before booking integrity.

### Database

- First optimize indexes, query plans, batch boundaries, and N+1 access; use PgBouncer/managed pooler only if compatible and verified.
- Bound DB pool to provider connection limits; use transaction duration metrics and avoid external network calls in transactions.
- Strong consistency remains primary for seat/quota/payment/booking writes. Read replicas are optional for noncritical reporting and introduce replication lag; never route allocation or “confirmed” reads to a lagging replica.
- Consider monthly partitions only for large append-only history after measured table size and FK/unique constraints are tested. Do not shard the run inventory prematurely.
- A hot run/date/class is a logical hot key. Serialize the minimal inventory decision with row locks; queue or rate-limit requests rather than weakening integrity.

### Cache and search

Cache stable public station/route/schedule data with versioned invalidation and TTL. Availability is a short-lived estimate at most and never an allocation decision. Redis loss must degrade to database-backed behavior with tighter limits; do not store durable holds or payment state there.

PostgreSQL B-tree/GIN/full-text indexes are the initial search solution. Rebuildable OpenSearch projection is a later option only when measured query volume/content needs justify it. Index updates are asynchronous and display freshness.

### Async workloads

Transactional outbox prevents a committed booking from losing its notification/event. PostgreSQL jobs provide durable retries, leases, backoff, dead-letter/error state, and operator replay. Apply bounded worker concurrency and queue backpressure. Kafka is a future migration option; it adds broker operation, partitions, retention, schema, and consumer recovery costs, and is not justified for the initial single-database product.

### CDN and static assets

Static app assets and public, immutable catalogue resources can be delivered through a CDN. Never CDN-cache personalized ticket data, private PNR responses, payment state, or unverified availability. Apply explicit cache-control and purge/version semantics.

## Consistency map

| Workload | Consistency |
|---|---|
| Seat overlap, quota usage, booking idempotency, reservation transitions | PostgreSQL transaction + constraints; strong |
| Payment event deduplication, refund totals, ledger posting | PostgreSQL transaction + unique keys/locks; strong |
| Admin rule publication and role assignments | Versioned transaction + audit; strong |
| Search index, analytics, notifications, dashboard aggregates | Eventual; freshness and retry visible |
| Redis cache, edge static content | Disposable; stale cache never authorizes a booking |

## Flash booking behavior

Protect the database with bounded concurrency, per-run admission limits/queues, short transactions, deterministic seat candidate order, bounded retries, and explicit `429`/`503` with retry hints. Availability may remain readable under load but carries freshness. If authoritative DB is unavailable, disable booking and payment state mutation; never “optimistically” confirm inventory.

## Capacity validation

The test plan must include load, stress, soak, hot-key contention, database restart, worker restart, provider delay, cache loss, and rollback. Record exact deployment size and free-tier limits. Target traffic is accepted only when p95/p99, error budget, data invariants, recovery time, and cost are all within approved bounds. Any capacity claim expires after material code, schema, data volume, or provider change.

## Evolution triggers

- Add read replica when query load measurably starves write latency and provider supports it without breaching cost budget.
- Add Redis for a measured cache/rate-limit need, with fallback test and explicit command budget.
- Add OpenSearch when PostgreSQL search misses the approved latency/relevance target at realistic scale.
- Add Kafka or service decomposition only with independent scaling/ownership/failure isolation evidence and a funded operational plan.
- Partition/archive when measured table/index maintenance and query latency justify complexity.

All such changes require a fresh zero-cost audit and explicit user approval if they introduce service, recurring cost, payment method, or data-transfer exposure.
