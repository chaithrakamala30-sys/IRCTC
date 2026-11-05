# Implementation roadmap — Phases 1–20

This is a dependency-ordered proposal. Phase 1 was explicitly approved and completed as a foundation; Phase 2 and later phases are not authorized. Each phase has its own acceptance gate. Real inventory, real payments, and external railway integrations remain off until their separate gates pass.

## Phase summary

| Phase | Objective | Depends on |
|---|---|---|
| 1 | Repository, build, local development, CI baseline | Phase 0 approval |
| 2 | Identity, sessions, RBAC, audit foundation | 1 |
| 3 | Station/train catalogue | 1, 2 |
| 4 | Routes, schedules, dated train runs, operations baseline | 3 |
| 5 | Classes, coach layouts, physical inventory model | 4 |
| 6 | Versioned fares, quotas, policy/rule evaluation | 3, 5 |
| 7 | Admin inventory imports and database integrity | 5, 6 |
| 8 | Search and segment availability | 4, 6, 7 |
| 9 | Reservation, holds, idempotency, concurrency | 2, 7, 8 |
| 10 | Payment abstraction and internal/sandbox test provider | 9 |
| 11 | Verified callbacks, booking confirmation, ticket/PNR | 9, 10 |
| 12 | Cancellation, refunds, reconciliation | 11 |
| 13 | Disruption and run operations | 4, 9, 11 |
| 14 | Admin application and approval workflows | 2–7, 13 |
| 15 | Passenger application and accessibility | 2–3, 8–12 |
| 16 | Support and durable notifications | 2, 11–15 |
| 17 | Finance, audit, operational reporting | 11–16 |
| 18 | Security, correctness, performance, and recovery hardening | 1–17 |
| 19 | Cost-checked staging and production-readiness evidence | 18 |
| 20 | Explicit launch decision and approved integration expansion | 19 + approvals |

## Phase details

### Phase 1 — Engineering foundation
- **Scope/files:** repository conventions, Java/Next.js project roots, build manifests, Docker/Compose local setup, CI workflows, `.gitignore`, contribution/developer instructions.
- **Database/API:** local PostgreSQL only; no business tables or public API beyond health endpoint.
- **Security:** secret scanning, dependency lock/pinning, no secrets in artifacts, least-privilege CI.
- **Tests/exit:** clean build, lint/typecheck/unit test, local DB startup and teardown, CI green from clean checkout; generated artifacts reproducible. **DB changes:** none.

### Phase 2 — Identity and access control
- **Scope/files:** identity-access module, session service, password/MFA workflow, role/permission and resource-scope model.
- **Database/API:** users, credentials, sessions, MFA, roles/permissions, audit baseline; `/auth/*`, `/me`, admin identity endpoints.
- **Security:** Argon2id benchmark, CSRF/session rotation, recovery, enumeration resistance, privileged MFA.
- **Tests/exit:** auth/session tests, IDOR/role/scope negative matrix, revocation and lockout tested; privacy fields approved.

### Phase 3 — Station and train catalogue
- **Scope/files:** railway-catalog domain/services/repositories, admin draft/review/publish flows.
- **Database/API:** station, train, provenance tables; `/stations`, `/admin/stations`, `/admin/trains`.
- **Security:** admin scope and audit; validated imports and no untrusted raw HTML.
- **Tests/exit:** unique code/number, effective status, duplicate import, approval and audit; data owner/provenance specified.

### Phase 4 — Routes, schedules, and train runs
- **Scope/files:** route versioning, ordered stops, timezone and run generator, operations baseline.
- **Database/API:** route_versions, train_stops, schedules, runs, run_stops/status; schedule/run endpoints.
- **Security:** only approved staff publish; immutable history of route snapshots.
- **Tests/exit:** stop-order, overnight offsets, local date/time conversion, DST boundary, route edit does not mutate prior run.

### Phase 5 — Classes, coaches, and seat model
- **Scope/files:** class/coach templates, berth/seat type metadata, run-specific snapshots.
- **Database/API:** class, coach template, coach seat, run coach/seat tables; admin layout endpoints.
- **Security:** no public access to internal seat maps beyond approved availability; imports audited.
- **Tests/exit:** duplicate seat IDs, mismatched class/coach, effective layout version and immutable run snapshot verified.

### Phase 6 — Versioned fares and rules
- **Scope/files:** deterministic rule evaluator, quote module, approval/version lifecycle.
- **Database/API:** rule sets/versions, fare/cancellation/quota rules, fare quotes; quote and admin rule APIs.
- **Security:** no unreviewed rule publication; input validation and rule provenance.
- **Tests/exit:** integer/exact amount arithmetic, rule boundary/effective-date tests, reproducible quote from persisted inputs; business policy owners approve semantics.

### Phase 7 — Inventory ingestion and integrity
- **Scope/files:** dry-run importer, validation report, run inventory loader, integrity monitoring.
- **Database/API:** journey/quota inventory, holds/allocations scaffolding and exclusion constraint; admin import endpoints.
- **Security:** import authorization, file limits, audit, no direct SQL client access for operators.
- **Tests/exit:** PostgreSQL overlapping segment exclusion, rollback of bad bulk import, repeated import idempotency, quota capacity checks.

### Phase 8 — Search and availability
- **Scope/files:** station/train search, query optimization, availability calculation.
- **Database/API:** indexes/full text; search/run/availability endpoints.
- **Security:** bounded search horizon, rate limits, privacy-safe logs.
- **Tests/exit:** valid route/stop sequence, segment-aware availability, freshness response, stale cache never treated as authority; query plan and NFR baseline recorded.

### Phase 9 — Reservations and holds
- **Scope/files:** reservation aggregate/state transitions, allocation service, hold expiry worker, idempotency handling.
- **Database/API:** reservations, segments, passengers, idempotency and job/outbox; booking create/read endpoints.
- **Security:** ownership checks, anti-automation limits, atomic transactions.
- **Tests/exit:** double-book, multi-passenger atomicity, duplicate/replay, expiry race, restart recovery, deadlock/retry tests pass on PostgreSQL.

### Phase 10 — Payment abstraction and test provider
- **Scope/files:** provider port, test adapter, payment attempt model, test-only payment UI indicators.
- **Database/API:** payment attempts; create/query test payment endpoints.
- **Security:** no card collection; test provider disabled in production config.
- **Tests/exit:** deterministic success/fail/pending/timeouts, config guard verified; sandbox credentials absent from CI.

### Phase 11 — Provider event processing, confirmation, ticket/PNR
- **Scope/files:** webhook adapters, verification, reconciliation poller, ticket issuance/reference generator.
- **Database/API:** provider events, ledger, ticket/PNR; webhook, status, ticket, PNR endpoints.
- **Security:** signed callback verification, replay detection, sensitive identifier protection.
- **Tests/exit:** duplicate/delayed/forged callbacks and late success after expiry; one durable effect and valid ticket transition.

### Phase 12 — Cancellation and refunds
- **Scope/files:** cancellation rule evaluation, refund workflow, reconciliation dashboard commands.
- **Database/API:** refund and ledger entries; cancellation/refund APIs.
- **Security:** finance authorization, approval thresholds, separation of duties.
- **Tests/exit:** duplicate cancellation, concurrent refund ceiling, partial refund arithmetic, provider failure/unknown status.

### Phase 13 — Railway operations
- **Scope/files:** run status/disruption workflows, passenger impact calculation, announcement records.
- **Database/API:** run status/disruption events; scoped operational endpoints.
- **Security:** reason/approval/audit for operational mutation; no unauthorized rail-system integration.
- **Tests/exit:** disruption impact, run history, affected reservation query, compensation/notification tasks do not mutate inventory silently.

### Phase 14 — Administrative application
- **Scope/files:** admin navigation/pages, station/train/route/schedule/coach/inventory/rule/user/finance/audit screens.
- **Database/API:** uses admin APIs from Phases 2–7 and 12–13; no client DB access.
- **Security:** staff MFA, scope-aware UI plus server enforcement, approval and audit visibility.
- **Tests/exit:** role-by-page/action matrix, accessibility baseline, sensitive actions require reason and server revalidation.

### Phase 15 — Passenger application
- **Scope/files:** home/search/results/details/availability/passenger/checkout/confirmation/ticket/PNR/cancel/refund/profile pages.
- **Database/API:** consumes versioned REST; no fabricated data or client-side authoritative decisions.
- **Security:** safe session handling, CSRF, XSS protections, PII minimization.
- **Tests/exit:** end-to-end simulated booking, keyboard/screen-reader/zoom, clear freshness and payment simulation labels.

### Phase 16 — Support and notifications
- **Scope/files:** case management, templates/preferences, durable notification worker.
- **Database/API:** support cases, delivery records/outbox handlers; support and notification endpoints.
- **Security:** least-privilege PII views, redaction, consent/preferences and safe links.
- **Tests/exit:** retries/dead-letter/manual replay, duplicate suppression, provider down behavior, access audit.

### Phase 17 — Finance and operational reporting
- **Scope/files:** reconciliation dashboards, inventory/booking/ops metrics, privacy-preserving reports.
- **Database/API:** reporting projections/materialized views only where justified; finance/report endpoints.
- **Security:** finance role boundaries, PII masking, export controls.
- **Tests/exit:** ledger reconciliation, freshness labels, report totals vs source, export audit and bounded data ranges.

### Phase 18 — Hardening and verification
- **Scope/files:** address security review findings, concurrency/performance optimization, operational runbooks and accessibility fixes.
- **Database/API:** migration/index/query tuning only from evidence; no API contract break without versioning.
- **Security:** threat model, OWASP verification, dependency/SBOM review, secret rotation exercise.
- **Tests/exit:** no unresolved release-blocking finding; contention/load/soak/restart/restore evidence meets approved SLOs.

### Phase 19 — Cost-checked staging and readiness
- **Scope/files:** infrastructure as code/config, deployment pipeline, backup/restore, monitoring, on-call and incident runbooks.
- **Database/API:** staged release with masked/synthetic data; migrations and reconciliation verified.
- **Security:** provider account MFA/least privilege, data-processing and privacy review, payment sandbox only.
- **Tests/exit:** verified current ₹0 usage plan, free-tier limitation exercise, restore RPO/RTO evidence, no automatic paid upgrade, production gate checklist signed. If impossible at ₹0, stop; do not bypass.

### Phase 20 — Explicit launch / authorized integrations
- **Scope/files:** only approved production config, migration, domain integration, and incremental provider adapter.
- **Database/API:** real data migration only with provenance, authorization, reconciliation, and rollback plan.
- **Security:** legal/compliance sign-off, payment/provider security review, incident contacts, user disclosures.
- **Tests/exit:** owner explicitly accepts continuity/cost/security posture; inventory source authorization; live payments/provider fees disclosed; real booking, backup, monitoring, support and rollback rehearsed. If any gate is unmet, no launch.

## Approval gates

Each phase closes with code review, updated documentation, test evidence, data/security review, and explicit completion decision. A failure in integrity, security, cost, data provenance, or recovery blocks dependent phases. Phase 20 is not an automatic release action and may be declined even when preceding implementation is complete.
