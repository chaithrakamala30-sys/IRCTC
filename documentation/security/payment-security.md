# Payment & Refund Security

## 1. Dhruva Bank Demonstration Gateway Security
- All transactions interact with the Dhruva Bank demonstration platform (`https://dhruva-bank.vercel.app`).
- **No Real Funds**: Demonstrated balances are educational simulations; no banking credentials (PINs, CVVs, card numbers) are stored or captured.

## 2. Webhook & Callback Integrity
- **Idempotency**: Webhook callbacks verify `Idempotency-Key` headers to ensure payments are not confirmed multiple times.
- **Backend Verification**: No payment is marked `SUCCESS` purely on browser redirect; server-to-server callbacks are verified against merchant transaction tokens.
- **Refund Safeguards**: Cumulative refunds are capped at the original booking total. Duplicate refund requests are rejected immediately.
