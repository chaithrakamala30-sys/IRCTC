# Functional requirements

Requirements are proposed product behavior, not an assertion that current railway policy has been verified. A requirement dependent on railway policy stays disabled until an authorized owner supplies an approved, versioned rule set.

## Passenger-facing

| ID | Requirement |
|---|---|
| FR-PASS-001 | A passenger can register, authenticate, sign out, recover access, and manage security settings using secure server-side identity controls. |
| FR-PASS-002 | A passenger can search approved RailConnect stations and trains by origin, destination, journey date, and supported class. |
| FR-PASS-003 | Search results show only administratively published schedules and clearly distinguish unavailable, unknown, and available inventory. |
| FR-PASS-004 | A passenger can inspect a train's route, stop sequence, timetable, operating days, classes, fare basis, and last-updated time. |
| FR-PASS-005 | A passenger can request availability and a fare quote for a specified journey segment, class, quota, date, and passenger count. Quotes state expiry and rule version. |
| FR-PASS-006 | A passenger can enter passenger and contact details, review the final fare, accept applicable terms, and submit an idempotent booking request. |
| FR-PASS-007 | A passenger can view the reservation state, payment state, ticket, booking reference / PNR, passenger allocation, and journey details for their own reservation. |
| FR-PASS-008 | A passenger can request cancellation for an eligible reservation and see the applicable rule version, cancellation outcome, and refund status. |
| FR-PASS-009 | A passenger can access booking history, profile, privacy choices, and support channels with access controls. |
| FR-PASS-010 | Passenger pages support keyboard navigation, labels, error summaries, responsive layouts, and accessible status messages. |

## Railway administration

| ID | Requirement |
|---|---|
| FR-ADMIN-001 | Authorized users can create, review, approve, publish, and retire stations, with unique code, name, timezone, and provenance. |
| FR-ADMIN-002 | Authorized users can manage trains, route stop sequence, distance/duration metadata, operating schedules, and effective dates. |
| FR-ADMIN-003 | Authorized users can manage coach layouts, seat identifiers, berth type, accessibility attributes, and class membership. |
| FR-ADMIN-004 | Authorized users can load inventory for a concrete train run and journey segments; bulk changes are validated and auditable. |
| FR-ADMIN-005 | Authorized users can define fare, quota, berth-preference, cancellation, waitlist, and refund rules as immutable versions with effective ranges and approver. |
| FR-ADMIN-006 | Authorized users can manage accounts, roles, permissions, and scoped station/region assignments; privileged changes require step-up authentication. |
| FR-ADMIN-007 | The platform exposes searchable audit history for configuration and sensitive operations without allowing ordinary operators to alter or delete it. |

## Reservation operations

| ID | Requirement |
|---|---|
| FR-RES-001 | The system resolves a permanent train route and schedule into a concrete, timezone-aware train run for a journey date. |
| FR-RES-002 | Availability is computed for the exact half-open journey segment, class, quota, and run using authoritative database state. |
| FR-RES-003 | Booking allocates all requested passenger inventory atomically or allocates none; a physical berth cannot overlap another active allocation on the same run. |
| FR-RES-004 | A booking request is idempotent; repeat requests with the same key and payload return the original outcome, while key reuse with a different payload is rejected. |
| FR-RES-005 | Temporary holds have bounded expiry, explicit state, and recovery after process restart; expiration cannot depend on in-memory timers. |
| FR-RES-006 | Confirmation requires a valid payment outcome or explicitly configured zero-value payment path and successful inventory transition. |
| FR-RES-007 | RAC and waitlist outcomes, promotion ordering, cancellation, and chart-preparation cutoffs use approved, versioned rules rather than frontend logic. |
| FR-RES-008 | Cancellation releases inventory and records a refund obligation in one transaction; external refund execution is asynchronous and idempotent. |
| FR-RES-009 | Train disruptions and schedule changes are recorded with author, reason, effective time, impacted runs, and passenger-notification status. |

## Financial operations

| ID | Requirement |
|---|---|
| FR-FIN-001 | A payment attempt is associated with one reservation, amount, currency, provider reference, and idempotency key. |
| FR-FIN-002 | Test-provider transactions are visibly marked simulated and cannot be mistaken for settled money. |
| FR-FIN-003 | Provider callbacks are authenticated, signature-verified, replay-safe, persisted before acknowledgement, and reconciled with provider status. |
| FR-FIN-004 | The system records immutable payment/refund ledger events; adjustments are compensating entries, not edits to history. |
| FR-FIN-005 | Refunds support full/partial amounts only under a versioned rule and authorized approval; status remains pending until provider-confirmed. |
| FR-FIN-006 | Finance users can review discrepancies, settlement/reconciliation batches, refund aging, and audit history. |

## Customer support

| ID | Requirement |
|---|---|
| FR-SUP-001 | A support officer can locate a case using approved identifiers and see only fields required for the case. |
| FR-SUP-002 | Support actions are scoped, reason-coded, audited, and cannot directly overwrite reservation/payment ledger state. |
| FR-SUP-003 | Passenger inquiries and complaint status are recorded with timestamps, ownership, and retention controls. |

## Security

| ID | Requirement |
|---|---|
| FR-SEC-001 | Every protected action is authorized server-side using least-privilege roles and resource ownership checks. |
| FR-SEC-002 | Secrets, authentication factors, and payment credentials are not exposed in logs, analytics, client bundles, or error responses. |
| FR-SEC-003 | Authentication, booking, search, payment initiation, webhook, and administrative endpoints have purpose-specific abuse limits. |
| FR-SEC-004 | Sensitive administrative and financial actions are attributable, tamper-evident, and auditable. |
| FR-SEC-005 | Users can exercise approved access, correction, and deletion/retention workflows subject to legal and financial record constraints. |

## Infrastructure and operations

| ID | Requirement |
|---|---|
| FR-OPS-001 | The service exposes liveness/readiness/health status without revealing credentials or internal topology. |
| FR-OPS-002 | Critical booking/payment state is durable in PostgreSQL; caches and process memory are disposable. |
| FR-OPS-003 | Scheduled cleanup and outbox work is safe after restart, retryable, observable, and idempotent. |
| FR-OPS-004 | Every dependency has an explicit free-tier limit, spend guard, outage behavior, and owner. |
| FR-OPS-005 | Deployment and database changes are versioned, reviewed, reproducible, and rollback-aware. |

## Reporting and analytics

| ID | Requirement |
|---|---|
| FR-REP-001 | Authorized users can view booking counts, occupancy, cancellations, revenue events, refunds, and payment reconciliation by approved dimensions. |
| FR-REP-002 | Operational dashboards identify disrupted runs, failed/aging holds, outbox lag, and booking integrity exceptions. |
| FR-REP-003 | Analytics use minimized or pseudonymized data and do not become an alternative source of reservation truth. |
| FR-REP-004 | Reports disclose timestamp, timezone, data freshness, aggregation scope, and incomplete-source warnings. |

## Acceptance policy

Requirements become implementation acceptance criteria only after owners, data provenance, policy versions, and legal/operational approval are identified. Do not fabricate train, station, fare, quota, waiting-list, or cancellation data to satisfy a demo.
