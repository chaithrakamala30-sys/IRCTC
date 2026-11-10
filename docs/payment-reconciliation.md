# Payment reconciliation

## Current status

Passenger-triggered status refresh calls Dhruva Bank's documented status endpoint and invokes authoritative verification on a reported success. Mismatches and late success are stored in reconciliation/financial-exception records and visible in the read-only finance administration view. No scheduled reconciliation worker, automated retry, refund, or exception-resolution action is implemented.

V12 creates reconciliation records and financial exceptions; it does not schedule background provider requests. Bookings with an unresolved provider outcome retain their provisional allocation after the payment deadline. A passenger retry reuses the same provider idempotency key; otherwise, the hold remains until manual investigation. No automated timeout or manual resolution endpoint is implemented. A later verified success after the booking expires is not confirmed and becomes a financial exception.

## Required safe behavior

- Do not create a second payment intent merely because a request timed out; retries use the same provider idempotency key.
- Treat provider/merchant status disagreement as `REQUIRES_RECONCILIATION`; preserve evidence and avoid confirmation.
- Verify amount, currency, merchant reference, booking state, and inventory validity independently.
- If verified success arrives after the allocation expires, create an investigation/refund exception; do not allocate another passenger's berth or confirm an invalid booking.
- Allocation expiry is controlled by the existing booking deadline; late verified success is investigated and never steals another passenger's berth.
- Keep refund execution out of scope; record future refund handling as a financial exception only.

Scheduled reconciliation, refunds, settlement, chargebacks, and financial reconciliation require a later approved phase and additional provider contract details.
