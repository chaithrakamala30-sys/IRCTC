# Testing strategy

## Test pyramid and environments

Use fast unit tests for deterministic domain rules; PostgreSQL-backed integration tests for constraints/locking/transactions; API contract tests for access and error shapes; browser end-to-end tests for passenger/admin workflows; then targeted concurrency, security, load, soak, and recovery suites. A test is not a substitute for production policy approval.

All booking/payment tests use isolated synthetic data and a deterministic simulated provider. No real card data, live payment, unauthorized railway endpoint, or third-party production data is used.

## Coverage matrix

| Layer | Scope | Required evidence |
|---|---|---|
| Unit | State transitions, rule version selection, fare arithmetic, segment overlap, eligibility | Boundary cases, property tests for interval overlap and amount invariants |
| Database integration | Flyway, FK/check/unique/exclusion constraints, locks, isolation, outbox, rollback | Test on supported PostgreSQL version; verify competing transactions |
| API | Validation, authn/authz, idempotency, pagination, rate limit, RFC 9457 errors | OpenAPI contract and negative tests per endpoint |
| End-to-end | Search-to-book simulation, user dashboard, ticket access, cancellation, admin approvals | Keyboard/accessibility checks and unauthorized cross-user scenarios |
| Payment | Signature, callback dedupe, delayed/contradictory status, refund/reconciliation | Durable ledger assertions and exactly-once business effect |
| Security | SAST/dependency/secret scanning, DAST, authorization review, abuse limits | Findings triaged; release blocker policy |
| Load/stress/soak | Normal/busy/flash profile, hot run, connection pressure, queue backpressure | Percentiles, errors, resource limits, invariant checks |
| Recovery | Process kill/restart, worker lease reclaim, DB unavailable/restore, provider outage | Verified RPO/RTO, no lost committed booking/payment events |

## Critical correctness scenarios

| Scenario | Setup / action | Assertions |
|---|---|---|
| Double booking | N concurrent requests for same seat and overlapping segment | At most one active allocation; exclusion constraint rejects conflict; loser gets explicit response |
| Adjacent segments | Allocate `[1,3)` and `[3,5)` on same seat | Allowed only if business rule permits; `[2,4)` must conflict |
| Different run | Same seat code on two run IDs | No false conflict across runs |
| Multi-passenger atomicity | Capacity for one passenger only, request two | No passenger allocation/reservation partial commit |
| Quota race | Concurrent allocations at last quota capacity | Used count never exceeds configured capacity |
| Duplicate booking | Same key/hash concurrently and after response loss | One reservation/payment intent; all retries resolve to same result |
| Idempotency mismatch | Reuse key with changed payload | Conflict; no second side effect |
| Hold expiry race | Payment success concurrent with expiry sweeper | One valid transition; no seat stolen and no silent paid failure |
| Lost/delayed callback | Provider reports success after callback delay or API restart | Durable pending state; reconciliation produces one result |
| Duplicate callback | Same provider event delivered repeatedly | One event/ledger effect; duplicate ack only after durable prior record |
| Forged callback | Invalid signature, stale timestamp, wrong amount/currency | Rejected/quarantined; no reservation confirmation |
| Cancellation race | Duplicate cancel and payment/refund callback concurrently | One cancellation/refund obligation; ledger totals valid |
| Refund ceiling | Concurrent partial refunds near remaining amount | Total refund never exceeds approved settled refundable amount |
| Invalid transitions | Attempt terminal -> active or direct client “paid” update | Rejected and audited; no state corruption |
| Worker restart | Kill worker after external effect/before DB completion | Retry idempotently; no duplicate notification/refund/ledger event |
| Migration compatibility | Upgrade from previous schema with active holds | No lost allocation; migration/release gates pass |

## Concurrency test method

Use independent database connections and synchronized barriers to create contention. Assert persisted state after all transactions complete, not only HTTP responses. Exercise realistic stop ranges, same/different quota, deadlocks, transaction retries, and DB pool saturation. Run invariant SQL checks before/after test:

- no overlapping `HELD` / `CONFIRMED` seat ranges per run/seat;
- no quota usage above configured capacity;
- no duplicate provider event;
- no duplicated idempotency business result;
- ledger/refund arithmetic reconciles.

Do not use H2 as the only evidence for PostgreSQL exclusion constraints, isolation behavior, or `SKIP LOCKED`.

## Performance and resilience tests

Use the [NFR profiles](02-non-functional-requirements.md) with warm and cold-start measurements separated. Run load, short stress, and soak; include hot-key spikes, cache disabled, slow DB, full pool, outbox backlog, worker restart, webhook retry storm, and provider timeout. Do not load test public provider endpoints or third-party services without authorization.

## Security and privacy tests

Test IDOR across passenger bookings, staff scopes, step-up operations, CSRF, XSS, SQL injection defenses, SSRF allowlists, rate limits, webhook signatures/replay, secret leakage, data masking, and security headers. Test logs and error bodies with synthetic PII/credentials to ensure redaction.

## Accessibility and quality gates

Run automated accessibility scans plus manual keyboard and screen-reader testing. Target WCAG 2.2 AA but do not claim conformance until evidence review. CI gates on tests, migration validation, OpenAPI compatibility, secret scan, dependency policy, and build reproducibility; high/critical security or integrity failures block release.

## Disaster recovery exercises

At least before production and after material provider/data architecture changes: restore independent backup, verify checksum/schema, run booking/payment integrity queries, measure RTO/RPO, test credential rotation, and document operator steps. Free-tier provider backups alone do not satisfy this requirement.
