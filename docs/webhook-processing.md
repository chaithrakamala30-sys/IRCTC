# Webhook processing

## Status: not implemented

The supplied Dhruva Bank contract covers payment-intent creation, status lookup, authoritative verification, transaction lookup, and account summary. It does not specify webhook delivery, payload shape, exact-body signing, signature headers/keys, replay protection, event identifiers, retries, or acknowledgement rules.

RailConnect therefore has no webhook route, signature verifier, or event processor. Flyway V12 includes tables reserved for future event evidence and attempts, but they are unused. No unsigned or guessed webhook may update a payment or booking.

Once Dhruva Bank supplies the webhook contract, implement exact-byte signature verification, provider-specific replay protection, unique-event idempotency, durable evidence before acknowledgement, payment/booking reference and amount validation, and reconciliation for unmatched or delayed events. These controls are requirements for a future change, not current behavior.
