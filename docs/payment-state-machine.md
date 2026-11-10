# Payment state machine

## States

`CREATED`, `INITIATED`, `CHECKOUT_PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`, `REQUIRES_RECONCILIATION`, and `REVERSED` (only if the provider contract supports a verified reversal).

Payment status is stored separately from booking and inventory status. No payment transition is implemented as a booking confirmation in this phase.

## Allowed transitions

- `CREATED -> INITIATED | CANCELLED | EXPIRED`
- `INITIATED -> CHECKOUT_PENDING | PROCESSING | FAILED | CANCELLED | EXPIRED | REQUIRES_RECONCILIATION`
- `CHECKOUT_PENDING -> PROCESSING | SUCCEEDED | FAILED | CANCELLED | EXPIRED | REQUIRES_RECONCILIATION`
- `PROCESSING -> SUCCEEDED | FAILED | CANCELLED | EXPIRED | REQUIRES_RECONCILIATION`
- `REQUIRES_RECONCILIATION -> CHECKOUT_PENDING | PROCESSING | SUCCEEDED | FAILED | CANCELLED | EXPIRED`
- `FAILED`, `CANCELLED`, `EXPIRED`, and `REVERSED` cannot advance normally; contradictory evidence may move them to `REQUIRES_RECONCILIATION`.

`PaymentStateMachine` rejects other transitions. Success requires independently verified provider status; webhook support is not implemented. Expiration requires an expiry or reconciliation process. A checkout return is never evidence of payment. Booking confirmation also requires the booking deadline and provisional allocation to remain live.

`REVERSED` is reserved in the model but no transition into it is enabled because the supplied provider contract does not document reversals. The migration has a matching database transition guard and append-only status history. Unknown provider states are routed to reconciliation.
