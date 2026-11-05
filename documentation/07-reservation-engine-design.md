# Reservation engine design

## Invariants

1. A seat is physical inventory: no two active allocations for the same run/seat may overlap in stop interval.
2. A multi-passenger command is atomic: all confirmed/held allocations are committed together, otherwise none.
3. Inventory and quota correctness are enforced by PostgreSQL constraints and row locks; cache and UI are advisory only.
4. Reservation state, payment state, refund state, and inventory state are separate, correlated state machines.
5. Every mutation is idempotent and attributable. No timeout or browser redirect means success.
6. Only approved/versioned rules can produce sellable availability, fare, RAC, waitlist, cancellation, and promotion decisions.

## State machines

### Reservation

`REQUESTED -> HELD -> PAYMENT_PENDING -> CONFIRMED`

From `HELD` / `PAYMENT_PENDING`: `EXPIRED`, `PAYMENT_FAILED`, `CANCELLED` (where permitted). A confirmed booking can transition to `CANCEL_PENDING -> CANCELLED`; an operational disruption can transition to `DISRUPTED` with a separate compensation decision. `REQUESTED` may resolve to `RAC` or `WAITLISTED` only when explicitly accepted by the user and allowed by a rule version. Waitlist promotion is a new guarded transition to `HELD`/`PAYMENT_PENDING` or `CONFIRMED`, according to approved policy. Terminal states do not return to active state; use a new reservation or audited compensating operation.

### Inventory

`AVAILABLE -> HELD -> CONFIRMED -> RELEASED`

`HELD -> EXPIRED` or `HELD -> RELEASED`; `CONFIRMED -> RELEASED` only after committed cancellation/disruption rule. Each state change occurs in the same DB transaction as reservation updates. `expires_at` is checked using DB time at transition, not trusted client time.

### Payment

`CREATED -> REQUIRES_ACTION -> PENDING -> SUCCEEDED | FAILED | EXPIRED | UNKNOWN`

`SUCCEEDED -> REFUND_PENDING -> PARTIALLY_REFUNDED | REFUNDED`

Provider uncertainty remains `UNKNOWN`/`PENDING` until verified reconciliation. A failed payment is not inferred from a missing callback. The payment state may be succeeded while the reservation remains unresolved if its hold expired; follow the approved recovery/refund path, do not claim a seat already reallocated.

Diagrams: [booking lifecycle](diagrams/booking-lifecycle.mmd), [reservation state](diagrams/reservation-state.mmd), [inventory allocation](diagrams/inventory-allocation.mmd).

## Atomic allocation procedure

1. Authenticate and authorize; validate passenger count, segment/run, rule versions, quote expiry, policy, and idempotency key.
2. In a transaction, insert/lock idempotency record and compare canonical payload hash.
3. Lock applicable quota capacity rows in stable order; verify total requested capacity across all segments.
4. Select eligible seats in deterministic order using row locks (`FOR UPDATE SKIP LOCKED`), excluding allocations whose segment ranges overlap. If no physical berth qualifies, use only approved RAC/waitlist outcome or reject.
5. Insert persisted holds and allocation ranges, then reservation/segments/passengers and payment intent; write outbox event. Database exclusion/unique constraints are final race barrier.
6. Commit. Only after commit create/redirect to a provider payment session. Provider network calls are never inside DB transaction.
7. Process callback/reconciliation idempotently; lock rows in a documented order, verify provider amount/currency/reference, then confirm or fail and release.

Quota rows, idempotency rows, reservation rows, and seat candidates are locked in a stable deterministic order to reduce deadlocks. Retry serialization/deadlock conflicts a bounded number of times with jitter; return a typed conflict/unavailable after exhaustion. Do not retry validation or permission errors.

## Holds, expiration, and interrupted work

Holds have a policy-approved short TTL and an explicit persisted `expires_at`. A database-backed worker claims expired holds with `FOR UPDATE SKIP LOCKED`, rechecks current state/time, transitions hold and allocation, updates reservation, and emits an outbox event in one transaction. Duplicate worker execution is harmless. Candidate allocation may opportunistically clean up stale rows under lock.

If the process restarts, persisted rows determine state; no in-memory timer is required. If the worker is down, stale held inventory may remain unavailable until recovery, which is preferable to double allocation. Alert on oldest expired hold and queue lag.

## Idempotency and duplicate requests

Scope key by authenticated principal and command type. Store key, canonical request hash, status, resource ID, response snapshot, and retention expiry. A duplicate with the same hash returns the prior committed result. Same key/different hash yields `IDEMPOTENCY_CONFLICT`. A concurrent insert is serialized by a unique constraint. Keep a safe replay window and never allow key expiration while an external payment outcome is unresolved.

## Availability semantics

Availability is calculated for a specific run, origin/destination stop range, class, and quota under a consistent DB snapshot. Public results are estimates until hold creation commits; include `asOf` / freshness and do not cache as authoritative. A live booking command rechecks capacity and constraints. Never expose a stale cache as confirmed availability.

## Cancellation and waitlist promotion

Cancellation decision, allocation release, reservation transition, refund obligation, and outbox record are transactional. Refund provider call runs after commit. Promotion claims the next eligible entry with row lock and stable policy ordering, rechecks passenger eligibility and inventory, creates a bounded offer/hold, and records the rule/version; failed/expired offers move according to approved policy. No promotion or queue ordering semantics are assumed here.

## Required sequence scenarios

Seven sequence diagrams are provided in [booking-lifecycle.mmd](diagrams/booking-lifecycle.mmd), [inventory-allocation.mmd](diagrams/inventory-allocation.mmd), and [event-flow.mmd](diagrams/event-flow.mmd): successful booking, payment failure, duplicate request, concurrent seat claim, delayed webhook, cancellation/release, and waitlist promotion.
