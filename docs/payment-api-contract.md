# RailConnect payment API

All endpoints require the existing authenticated session and CSRF protections. Passenger payment orders are owner-scoped. The browser never supplies amount, currency, provider status, or a booking-confirmation decision.

## Passenger routes

| Method and route | Purpose |
|---|---|
| `GET /api/v1/passenger/payments/providers` | Return configured provider availability; no credentials are included. |
| `POST /api/v1/passenger/payments/orders` | Start or idempotently retrieve a demo payment for an existing booking. |
| `GET /api/v1/passenger/payments/orders/{paymentId}` | Read own payment status. |
| `POST /api/v1/passenger/payments/orders/{paymentId}/refresh` | Query Dhruva Bank and, when its status is `SUCCEEDED`, call the authoritative `/verify` endpoint before applying success. |

Initiation body:

```json
{
  "bookingId": "uuid",
  "experience": "mobile"
}
```

`experience` is `mobile` or `laptop`. Send a unique `Idempotency-Key` header. The backend derives the booking reference, amount, INR currency, deadline, customer reference, and metadata from the authenticated user's booking. The response is the standard RailConnect API envelope and includes a payment id, booking reference, status, amount, currency, expiry, hosted checkout URL when available, and demo/reconciliation flags.

Checkout return pages do not trust the query-string `outcome`; they call the refresh route and read the booking again. Only a verified provider success with a still-live provisional allocation can confirm the booking. A late or contradictory verified success is held for reconciliation and recorded as a financial exception.

## Administration routes

| Method and route | Purpose |
|---|---|
| `GET /api/v1/admin/payments/orders?query=&page=0&size=20` | Search demo payment orders. |
| `GET /api/v1/admin/payments/orders/{paymentId}` | Read payment attempts, status history, webhook evidence, reconciliation records, and financial exceptions. |

Access is limited to `FINANCE_ADMIN` and `SUPER_ADMIN` through `payments:admin:read`. The administration UI is read-only and does not expose provider configuration or credentials.

## Not implemented

There is no webhook endpoint, refund route, settlement workflow, ledger posting, scheduled provider reconciliation worker, transaction-details proxy, or merchant-summary proxy. The provider contract has no webhook semantics; do not infer them from the persistence scaffolding.
