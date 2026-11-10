# RailConnect Pay architecture

## Implemented flow

1. Authenticated passenger requests payment for an existing booking. RailConnect derives amount, INR currency, booking reference, and expiry from the booking; each booking has at most one payment order.
2. Payment order, attempt, audit history, and idempotency data are persisted before the HTTP request. Provider creation occurs outside the database transaction and uses a stable provider idempotency key.
3. The Dhruva adapter sends the documented create-intent request using the backend Bearer secret and validates the response, amount, identifiers, expiry, and hosted checkout origin.
4. On return, the passenger app calls RailConnect's server-side status-refresh route. A `SUCCEEDED` status triggers the documented `/verify` call. RailConnect checks all payment identifiers and amount/currency before it asks the booking service to revalidate and confirm a still-live allocation.
5. Unknown, contradictory, or late success is not treated as a confirmed booking; it is recorded for reconciliation/financial review.

The mobile and laptop experiences have separate return pages but share the same owner-scoped backend API. Both label the flow as a demo and do not collect bank credentials.

## Persistence and boundaries

Flyway V12 adds provider configuration, request rate limiting, payment orders/attempts, idempotency, immutable state/audit history, future webhook evidence tables, reconciliation records, and financial exceptions. V13 allows a booking to transition to `CONFIRMED` only after verified payment and live allocation confirmation. Integer paise is the internal amount unit; the bank contract uses exact decimal INR.

Provider enablement is disabled by default. Secret values are not stored in PostgreSQL or returned to the frontend. The finance/super-admin view is read-only.

## Deliberate gaps

Dhruva Bank has not supplied webhook signing/replay/acknowledgement details. No webhook route or processor is implemented. Automated reconciliation, refund, settlement, double-entry ledger, provider transaction/account-summary proxy, and real-money support are also out of scope. See [the adapter contract](./dhruva-bank-integration.md) and [deployment limits](./payment-deployment.md).
