# Phase 8 — Booking Management Engine

## Scope and status

Phase 8 adds an authenticated booking aggregate on top of the existing Fare, Inventory, and Reservation modules. It creates a RailConnect booking reference; it does not create an official railway PNR, take payment, or confirm a ticket.

The normal Phase 8 booking state is `AWAITING_PAYMENT`. The booking amount and fare breakdown are copied from an immutable, owner-scoped Phase 5 quote. Berth selection remains owned by Phase 7. Booking requests do not accept passenger names, prices, or allocations from the browser; the server reads those from the authenticated user's persisted provisional reservation.

Implemented in this slice:

- Additive Flyway V11 tables and constraints for bookings, booking passengers, status history, idempotency, rate limiting, and audit.
- Passenger creation, paginated history, booking detail, reference lookup, fare, passenger, and status APIs.
- Secure random RailConnect reference generation, idempotent replay, quote/allocation matching, immutable fare and assignment snapshots, and expiry/release coordination.
- Mobile and laptop passenger booking creation and history views.
- A provider-neutral internal `BookingPaymentPort` contract for later integration; it has no Phase 9 implementation.

Not implemented or not yet verified: database-backed booking workflows, admin booking support APIs/UI, operations aggregates/UI, durable booking-failure reconciliation, transactional outbox, runtime portal tests, and performance benchmarks. The migration and application have not been connected to PostgreSQL in this environment; therefore V11 is not confirmed applied to Supabase or any other database. These are blockers to declaring the full checklist complete.

## End-to-end workflow

1. The passenger searches and selects a train journey using the existing search flow.
2. The passenger obtains an itemized fare quote. The quote is associated with the authenticated user when a passenger session is present.
3. The passenger creates one Phase 6 inventory hold per passenger, then submits and allocates a Phase 7 reservation request.
4. The passenger sends only `fareQuoteId` and `reservationRequestId` to the booking API with an `Idempotency-Key`.
5. In one PostgreSQL transaction, the booking service checks the idempotency record, reads the owner's active allocation and quote, checks run/date/stations/class/passenger count and quote expiry, verifies the quote's stored amount and currency, snapshots the values and assignments, inserts booking/passenger/history/audit records, attaches the allocation to the booking, and saves the idempotent resource reference.
6. The passenger sees a booking reference, berth assignments, fare breakdown, total payable amount, `AWAITING_PAYMENT` state, and deadline. This is explicitly not a paid or confirmed ticket.
7. At deadline, the booking expiration process rechecks booking and allocation states, releases the provisional allocation through Phase 7, and writes the `EXPIRED` transition. Repeated release and expiration processing is safe for already released/expired allocations.

### APIs

All endpoints require an authenticated passenger and the corresponding `bookings:own:*` authority. Private lookups return the same not-found response for absent and non-owned bookings.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/bookings` | Create from an owned active fare quote and owned provisional allocation |
| GET | `/api/v1/bookings` | Paginated history; optional `status`, `reference`, `page`, and `size` |
| GET | `/api/v1/bookings/{bookingId}` | Owner-scoped details, assignment, fare, and state history |
| GET | `/api/v1/bookings/reference/{bookingReference}` | Owner-scoped lookup by random RailConnect reference |
| GET | `/api/v1/bookings/{bookingId}/passengers` | Owner-scoped passenger and berth snapshots |
| GET | `/api/v1/bookings/{bookingId}/fare` | Owner-scoped fare snapshot and passenger breakdown |
| GET | `/api/v1/bookings/{bookingId}/status` | Owner-scoped booking state, deadline, and history |

Creation body:

```json
{
  "fareQuoteId": "uuid",
  "reservationRequestId": "uuid"
}
```

The API does not expose a passenger-triggered expiration endpoint. `Idempotency-Key` is required, is scoped to the authenticated user, and is compared with a SHA-256 fingerprint of both request references. Identical retries return the same booking; key reuse with another request returns `IDEMPOTENCY_KEY_REUSED`. Distinct keys cannot reuse a quote or reservation because database uniqueness constraints enforce one booking per quote and allocation request.

## State and expiration

Only these transitions are supported in Phase 8:

- `∅ -> AWAITING_PAYMENT` after all fare/allocation checks and the booking transaction succeed.
- `AWAITING_PAYMENT -> EXPIRED` after the stored deadline and a safe release/no-active-allocation check.

There is no arbitrary state update API. No Phase 8 code can mark a booking paid or confirmed. A booking is never expired merely because a payment callback is absent. Phase 9 must coordinate uncertain or pending provider results before releasing inventory; if provider outcome is uncertain, it must reconcile first.

The server chooses the deadline as the earlier of the configured payment window and Phase 7 allocation expiry. Configuration:

```yaml
railconnect:
  bookings:
    payment-window: PT10M
    expiration-sweep-ms: 30000
    requests-per-minute: 10
```

The payment window must be positive and no longer than 24 hours. The per-user rate counter is updated in a separate transaction so failed booking attempts still count. Booking reads check expiry before returning details; history opportunistically processes up to 100 due records and a scheduled sweep processes up to 50 at a time. Phase 7's own allocation expiry remains an independent safety net.

## Persistence and consistency

V11 adds:

- `bookings`: owner, random reference, run/journey keys, quote and allocation references, status, exact `NUMERIC` amount, currency, deadline, and immutable train/journey/fare snapshots.
- `booking_passengers`: immutable names, assigned coach/berth/type, per-passenger fare snapshot, and composite foreign keys tying the passenger and allocation to the same reservation request.
- `booking_status_history`: append-only state transitions and reason codes.
- `booking_idempotency_records`: unique user/key, request fingerprint, and response booking reference.
- `booking_request_rate_limits`: bounded per-user, per-minute creation attempts.
- `booking_audit_log`: append-only booking creation and expiry audit events.

Unique constraints prevent duplicate references, quote consumption, allocation-request consumption, passenger allocation reuse, and user-scoped idempotency key duplication. Database triggers prevent booking snapshot mutation, passenger snapshot mutation, history/audit mutation, invalid status changes, premature expiry, and mutation of completed idempotency records.

Fare quote reads and booking writes use the same PostgreSQL datasource and transaction manager. The Phase 7 allocation correlation is attached without changing the allocation to `CONFIRMED`; the database trigger permits that pending-booking association. Phase 9's verified success path may call the existing reservation confirmation service with the same booking ID. Booking creation failure rolls back the booking transaction and its association; the previously allocated Phase 7 request remains subject to its own expiry. No external network calls occur inside the transaction.

## Reference and snapshots

Booking references use `RC-` plus 12 cryptographically generated characters from an ambiguity-reduced alphabet. The database enforces uniqueness and format. This is a RailConnect reference, not an IRCTC PNR and not a predictable sequence.

The fare snapshot is the immutable Phase 5 persisted quote, including fare rule version, distance, passenger components, currency, and amount. Booking amount is taken from that quote only. Passenger snapshots preserve the allocation's assigned berth and passenger name at booking time. Historical reads do not recalculate fares or depend on subsequently edited master data.

## Phase 9 internal contract

`BookingPaymentPort` defines the future internal boundary for:

- Retrieving amount, currency, reference, and payment deadline for a payment attempt.
- Accepting verified success/failure outcomes using idempotent provider event references and verified amount/currency.
- Recording pending provider status and requesting reconciliation for uncertain outcomes.

There is no provider adapter, public payment endpoint, fake payment action, or payment state in Phase 8. The payment module must not write booking tables directly. It must not confirm unless the provider result is verified and matches booking amount/currency; repeated success must not double-confirm. A late verified success after inventory release requires reconciliation/recovery and must never steal a reallocated berth.

## Security and portal boundaries

- Booking routes require passenger role plus booking-specific permissions.
- Fare quote owner, reservation owner, and booking owner must match.
- Private booking IDs/references are ownership checked.
- Passenger details are available only on the passenger's own booking endpoints.
- The random reference has high entropy; reference search is still owner-scoped.
- Per-user booking creation attempts are rate limited.
- Passenger/admin allocation release is rejected after a reservation is attached to a booking; only the booking service can perform its expiry release.
- Admin and operations booking APIs/UIs are not present in this implementation. Do not treat Phase 7 reservation screens as booking administration.

## Diagrams

- [Booking workflow](diagrams/booking-workflow.mmd)
- [Booking state machine](diagrams/booking-state.mmd)
- [Fare quote to booking](diagrams/fare-to-booking.mmd)
- [Allocation to booking](diagrams/allocation-to-booking.mmd)
- [Booking expiry and release](diagrams/booking-expiration.mmd)
- [Future payment contract](diagrams/booking-payment-contract.mmd)
- [Booking failure recovery](diagrams/booking-failure-recovery.mmd)

## Verification and limitations

Executed locally:

- Maven backend unit tests: 31 passed, 0 failures, 0 errors, 0 skipped.
- Frontend tests: 10 passed; ESLint, workspace type check, and passenger-web production build passed.
- `git diff --check` passed.
- `DatabaseMigrationIT` was extended to assert V11 objects, but requires `RAILCONNECT_DB_URL`. That environment variable and `psql` were unavailable during this implementation, so PostgreSQL migration, transactional workflow, concurrency, and expiry tests were not run.

Additional required work before production readiness:

1. Apply V11 through Flyway only after checking the target database's current schema history; do not replay blindly.
2. Add PostgreSQL integration tests for migration validity, quote/allocation matching, transaction rollback, idempotent concurrency, ownership, expiry races, and release failure.
3. Implement durable workflow-failure/reconciliation records and retry visibility; current transaction errors are surfaced but are not persisted as booking failure cases.
4. Add authorized admin booking search/audit/failure views and privacy-limited operations aggregates in their separate portals.
5. Run mobile/tablet/laptop browser tests, API runtime tests, security tests, and performance measurements.
6. Decide and document retention, passenger-data access, and operational response policies before live use.

No payment gateway or paid infrastructure was added. Phase 9 has not started.
