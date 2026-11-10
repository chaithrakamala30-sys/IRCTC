# Payment security

## Implemented safeguards

- Provider is disabled by default. Its secret is backend-only and never stored in the database, frontend bundle, or logs.
- The adapter uses a bounded HTTP client, restricts configured provider origins, checks the hosted checkout URL against the configured origin/session path, and validates returned references and INR amount.
- Amounts are derived from the owner-scoped booking and converted exactly between decimal INR and integer paise; floating-point arithmetic is not used.
- Passenger routes require `PASSENGER` role plus payment permissions. Admin reads require `FINANCE_ADMIN` or `SUPER_ADMIN`; no route exposes merchant configuration.
- Payment initiation and status refresh use a persistent per-user rate limit (default 6 requests per minute).
- Payment idempotency binds a key to an owner and request fingerprint; the provider idempotency key is stable across retries.
- Success requires a server-side provider `/verify` response with matching intent, merchant reference, booking reference, amount, currency, verified flag, and completed transaction. The checkout return URL cannot set payment state.
- Booking and allocation are rechecked before confirmation. Late/contradictory success is not confirmed and creates reconciliation/financial-exception evidence.
- Payment history/audit and idempotency records are protected by database triggers. No card/UPI/PIN/bank credentials are collected by RailConnect.

## Explicitly out of scope

Dhruva Bank's webhook signature, replay, and acknowledgement contract was not supplied, so there is no webhook endpoint. Automated reconciliation, refunds, settlement, and a double-entry ledger are not implemented. The integration is demo-only and is not security certification or approval to process real money.
