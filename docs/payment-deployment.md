# Payment deployment

## Backend configuration

Payment enablement is opt-in. The default configuration is disabled and the provider secret is empty. In the backend's trusted secret store only, set:

- `RAILCONNECT_PAY_DHRUVA_BANK_ENABLED=true`
- `RAILCONNECT_PAY_DHRUVA_BANK_API_KEY` to the merchant API key
- `RAILCONNECT_PAY_DHRUVA_BANK_API_SECRET` to the merchant API secret
- `RAILCONNECT_PAY_DHRUVA_BANK_BASE_URL` to `https://dhruva-bank.vercel.app` (local development may use loopback HTTP)
- `RAILCONNECT_PAY_PASSENGER_ORIGIN` to the exact passenger-site origin for hosted-checkout returns

Never set the secret in Vercel, a browser variable, `.env.example`, or source control. The provider config row contains only enablement/demo flags and contract status. Keep payments disabled unless the provider origin and credentials are confirmed to point to a simulated, no-real-money environment.

Flyway V12 creates payment records and V13 adds booking confirmation after verified success. Apply them through the normal Flyway deployment process and verify the target database/migration history first. PostgreSQL integration testing requires a configured `RAILCONNECT_DB_URL`; no shared/Supabase database was used for development verification.

## Cost and production restrictions

No paid infrastructure or provider calls are introduced by a disabled default. Do not enable the adapter for real-money processing. Refunds, settlement, ledger posting, webhook processing, automated reconciliation, and payment compliance are not implemented. The webhook contract gap must be resolved before webhook support is considered.
