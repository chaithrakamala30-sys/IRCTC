# Payment architecture

## Separation and scope

Payment is a separate module with a provider-neutral interface. Reservation correctness does not depend on a particular gateway. The initial implementation is local-only or sandbox-only; an internal test provider must display **SIMULATED — NO MONEY MOVED** on every response and ticket/receipt. Never accept real card details in test mode.

Live payment is a later approval gate. Provider selection, transaction fees, settlement timelines, chargeback/dispute handling, merchant/KYC obligations, refund rules, and applicable payment compliance require current review. ₹0 infrastructure does not imply free payment transactions.

## Provider port

Conceptual operations:

- `createPaymentSession(reservationReference, amountMinor, currency, idempotencyKey, returnUrl)` -> provider session/reference and expiry.
- `verifyCallback(headers, rawBody)` -> authenticated provider event; reject invalid signature/timestamp.
- `queryPayment(providerReference)` -> verified normalized status for reconciliation.
- `requestRefund(paymentReference, amountMinor, idempotencyKey, reason)` -> provider refund reference/state.
- `health()` -> operational status only; never expose credentials.

Provider SDK/HTTP implementation is an adapter. Core code accepts only normalized, verified outcomes. Store provider name, opaque IDs, amount, currency, status, timestamps, and minimal event evidence; encrypt or strictly limit any retained webhook body.

## Payment lifecycle

`CREATED -> REQUIRES_ACTION -> PENDING -> SUCCEEDED | FAILED | EXPIRED | UNKNOWN`

`SUCCEEDED -> REFUND_PENDING -> PARTIALLY_REFUNDED | REFUNDED`

See [payment lifecycle](diagrams/payment-lifecycle.mmd). Provider statuses that cannot be mapped confidently remain `UNKNOWN` and enter reconciliation; no permissive fallback.

## Callback processing

1. Preserve exact request bytes for signature validation; enforce provider-specific size/time limits.
2. Verify signature, signed timestamp/nonce, expected endpoint/provider key version, and content type.
3. Insert provider event with unique `(provider, event_id)` before acknowledging; duplicate events return success only after confirming durable prior processing.
4. In a transaction, lock payment and reservation in consistent order; verify amount, currency, merchant/account, reservation reference, and legal transition.
5. Append immutable ledger entry and update state; create outbox event. Commit, then acknowledge provider.
6. Reconcile unmatched, stale, or contradictory callbacks through provider status query and finance queue.

Never trust browser redirect or client-supplied “paid” status. Do not acknowledge before durable persistence. Retain callback evidence only as long as justified by audit/privacy policy.

## Delayed, duplicate, or contradictory outcome

- Delayed callback: reservation remains pending until verified callback or scheduled query; don't report paid/confirmed prematurely.
- Duplicate callback: event unique key prevents a second ledger effect.
- Success arrives after hold expiry: inspect whether inventory remains protected. If safely reacquirable, apply approved recovery transaction; otherwise record money received and create a refund/reconciliation obligation, without stealing another passenger's berth.
- Provider timeout: status `UNKNOWN`; query with bounded backoff, alert after threshold. Do not release inventory solely on an ambiguous network error unless approved timeout policy and provider reconciliation establish a safe state.
- Callback claims amount/currency mismatch: quarantine event, do not confirm, and alert finance/security.

## Refunds and ledger

Refund request is an idempotent command; request/approval, provider execution, provider confirmation, and ledger posting are separate observable steps. Enforce cumulative refund ≤ settled, refundable amount under a payment-row lock and versioned cancellation policy. Partial refund allocation must be explainable by passenger/segment/fee rule. Corrections use compensating ledger entries; never edit/delete posted financial history.

Daily or scheduled reconciliation compares internal attempts, provider transactions, refunds, and settlements. Unmatched records enter a durable exception queue with owner, aging, and resolution evidence.

## PCI and sensitive data

Use a provider-hosted checkout/tokenization flow to keep card data out of RailConnect systems where feasible. Never store PAN, CVV, PIN, or magnetic-stripe data. Do not log card data, provider secrets, webhook signatures, session tokens, or unredacted payloads. Scope and obligations must be verified with the selected provider and qualified compliance assessor before live launch.

## Development test provider

Deterministic local scenarios: success, explicit failure, pending/delayed callback, duplicated callback, invalid signature, amount mismatch, timeout, refund success/failure, and late success after expiry. Test provider is impossible to select in production configuration; startup fails if a production profile points to it.
