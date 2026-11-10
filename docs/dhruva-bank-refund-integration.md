# Dhruva Bank Refund Integration

RailConnect communicates with Dhruva Bank via server-side merchant credentials (`x-merchant-key` and `x-merchant-secret`).

---

## Endpoints Utilized

1. **Request Refund**:
   `POST /api/v1/merchant/refunds`
   - Body: `payment_intent_id`, `merchant_refund_ref`, `cancellation_reference`, `amount`, `currency`, `idempotency_key`.

2. **Verify Authoritative Double-Entry**:
   `POST /api/v1/merchant/refunds/{refundId}/verify`
   - Returns double-entry ledger verification confirmation (`verified: true`, `ledger_entries_count: 2`).

3. **Query Status**:
   `GET /api/v1/merchant/refunds/{refundId}`
