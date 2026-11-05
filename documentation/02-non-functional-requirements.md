# Non-functional requirements

These are measurable design targets for later validation, not guarantees. They must be baselined against approved workloads, free-tier constraints, and realistic deployment hardware before launch.

## Service objectives

| ID | Attribute | Provisional target / method |
|---|---|---|
| NFR-PERF-001 | Search API | p95 ≤ 1.5 s at 20 requests/s in a documented representative test; exclude cold starts and report them separately. |
| NFR-PERF-002 | Availability API | p95 ≤ 1.0 s at 10 requests/s when warm; all results carry freshness and are not an inventory guarantee. |
| NFR-PERF-003 | Booking command | p95 ≤ 3 s at 5 booking attempts/s in a contention test; correctness takes precedence over latency. |
| NFR-PERF-004 | Cold start | Measure and disclose separately; free hosting can exceed interactive targets. No silent timeout-as-unavailable fallback. |
| NFR-SCALE-001 | Capacity claim | No concurrency or user-scale claim without repeatable load, soak, and recovery evidence on production-equivalent resources. |
| NFR-AVAIL-001 | Prototype | Best-effort only on free providers; no uptime SLA. |
| NFR-AVAIL-002 | Commercial launch | Requires an approved continuously available hosting/database/backup design; if ₹0 cannot meet it, block launch rather than weaken correctness. |

## Quality attributes

### Correctness and integrity

- **NFR-DATA-001:** No two active allocations may use the same physical seat on overlapping segments of the same train run.
- **NFR-DATA-002:** Reservation, inventory, and quota transitions are transactional; partial multi-passenger bookings are not committed.
- **NFR-DATA-003:** Replayed booking/payment/refund operations have one durable business effect.
- **NFR-DATA-004:** Store money in integer paise or exact decimal with currency; never use floating point for amounts.
- **NFR-DATA-005:** Persist timestamps as UTC instants; retain station-local timezone and display in Asia/Kolkata unless station-specific timezone is approved.
- **NFR-DATA-006:** Search indexes, cache values, and analytics are rebuildable projections, not authoritative records.

### Security and privacy

- **NFR-SEC-001:** Threat model and OWASP ASVS verification scope are approved before handling personal/financial production data.
- **NFR-SEC-002:** Passwords use Argon2id with parameters tuned to target runtime; MFA and session/refresh-token rotation are required for privileged users.
- **NFR-SEC-003:** TLS in transit; encrypt backups and managed storage where provider supports it; document limitations for free tiers.
- **NFR-SEC-004:** No PAN/CVV storage; payment tokens and provider identifiers are minimized and access-controlled.
- **NFR-SEC-005:** Administrative access follows least privilege, step-up verification for sensitive actions, and immutable audit trail.
- **NFR-PRIV-001:** Define data purpose, consent/legal basis, retention, access, correction, erasure exceptions, breach process, and processors before launch; obtain India-qualified legal review.

### Accessibility and usability

- **NFR-A11Y-001:** Target WCAG 2.2 AA for passenger and administrative interfaces; test keyboard-only, screen-reader, zoom/reflow, contrast, and error handling.
- **NFR-UX-001:** Booking status, payment status, fare quote expiry, data freshness, and simulated/live payment mode are explicit; never infer success from a redirect.

### Maintainability and portability

- **NFR-MAINT-001:** Business modules have explicit ownership and dependency rules; no module reaches another module's persistence internals.
- **NFR-MAINT-002:** Versioned Flyway migrations are forward-reviewed; rollback strategy is compensating migration or restore, not destructive manual SQL.
- **NFR-PORT-001:** Keep provider-specific code behind adapters; deploy the same container artifact locally and on compatible services.
- **NFR-PORT-002:** Keep business-critical state in PostgreSQL and avoid provider-specific edge/runtime dependencies in the core.

### Resilience and recovery

- **NFR-REC-001:** On worker/server restart, held inventory, payment callbacks, refunds, and outbox events reconcile from durable state.
- **NFR-REC-002:** Prototype recovery objective: documented RPO ≤ 24 h and RTO ≤ 24 h only where verified by a tested backup/restore process; free tiers may not satisfy it.
- **NFR-REC-003:** Production RPO/RTO must be set by business owner and validated with restore drills. No production go-live without backups independent of the primary database.
- **NFR-REC-004:** External dependency failure is surfaced as unavailable/pending, never converted into success or synthetic availability.

## Provisional traffic model

There is no observed traffic baseline. Use these initial *test profiles*, not market forecasts:

| Profile | Read traffic | Booking attempts | Purpose |
|---|---:|---:|---|
| Normal prototype | 5 req/s | 1 attempt/s | Functional baseline; single-region small DB. |
| Busy window | 20 req/s | 5 attempts/s | API latency and moderate inventory contention. |
| Flash-rush stress | 100 req/s | 25 attempts/s | Short-duration queueing, throttling, lock contention, and graceful degradation; not a launch capacity promise. |

Increase one dimension at a time; include hot run/date/class/quota and overlapping journey segments, not only uniform synthetic data. Report 1m/5m/15m windows, error classes, lock waits, DB pool saturation, queue lag, and cold starts.

## Test evidence and release gates

The [testing strategy](12-testing-strategy.md) defines validation. NFRs are accepted only with test environment, build hash, dataset shape, tooling, concurrency, duration, observed distributions, error budget, and known limitations recorded. Free-tier tests must respect provider policies and must not generate real payments or load on unauthorized railway systems.
