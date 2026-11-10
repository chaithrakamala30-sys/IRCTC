# Dhruva Bank Demonstration Platform Integration Report

## 1. Integration Scope
- Hosted Demo URL: `https://dhruva-bank.vercel.app`
- Repository: `https://github.com/dhanush10607-lang/Dhruva-bank.git`

## 2. Verified Integration Controls
- **Non-Monetary Demonstration Guarantee**: Displayed clearly across all checkout forms and administrative reconciliations. No real banking accounts, MPINs, or cards are queried.
- **Server-to-Server Verification**: Payments are verified using signed webhook payloads and merchant transaction references; frontend redirects are never trusted as proof of payment.
- **Idempotency & Replay Resistance**: Every webhook callback verifies `Idempotency-Key` headers, rejecting replay attempts.
- **Refund Settlement**: Refund requests debit demo merchant account and credit passenger demo account with double-entry integrity.

