# Dhruva Bank integration

## Contract implemented

RailConnect integrates only with the supplied Dhruva Bank Merchant Payment API v1. The adapter sends the documented `x-merchant-key` and `x-merchant-secret` headers; it does not implement HMAC signing. It calls:

- `POST /api/v1/merchant/payment-intents`
- `GET /api/v1/merchant/payment-intents/{paymentIntentId}`
- `POST /api/v1/merchant/payment-intents/{paymentIntentId}/verify`

The configured base URL is restricted to `https://dhruva-bank.vercel.app` or loopback HTTP for local development. Checkout URLs must match that exact origin and `/checkout/{session_id}` path. Requests and responses use the contract's INR decimal amount, translated exactly to integer paise inside RailConnect. Provider intent creation carries the stable provider idempotency key in the documented request body.

RailConnect subtracts a 30-second safety buffer from the remaining booking payment window when setting `expires_in_seconds`. The returned provider expiry is still validated against the booking deadline; Dhruva must not extend an intent beyond that deadline.

The adapter never exposes the merchant secret to a browser or stores it in PostgreSQL. It does not call the transaction-details or merchant-account-summary endpoints. Provider status `SUCCEEDED` is not accepted as proof by itself: RailConnect calls `/verify` and checks the intent/reference/amount/currency, `verified`, `COMPLETED` transaction, transaction reference, and verification timestamps.

## Enablement

Payments are disabled by default. To enable the **demo** adapter, configure the backend-only `RAILCONNECT_PAY_DHRUVA_BANK_ENABLED=true`, `RAILCONNECT_PAY_DHRUVA_BANK_API_KEY`, and `RAILCONNECT_PAY_DHRUVA_BANK_API_SECRET` values in a trusted backend secret store. The database configuration row is synchronized as enabled only when the adapter is enabled and both credentials are present. Do not set these on Vercel or in any `NEXT_PUBLIC_*` variable.

No live-money processing, settlement, refund, or banking credential collection is supported. Confirm the provider deployment is still a simulated environment before enabling it. This repository does not connect to, inspect, or modify Dhruva Bank's repository or database.

## Contract gap: webhooks

The supplied v1 contract does not define webhook delivery, event schemas, signature headers/canonicalization, timestamp/replay rules, or acknowledgement/retry behavior. RailConnect does not expose a webhook endpoint or process webhook events. V12 contains future-use evidence tables only; those tables do not imply webhook support. Do not implement or send webhook traffic until the provider supplies the complete contract.
