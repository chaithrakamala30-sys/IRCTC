# Cancellation Security Controls

---

1. **Ownership Validation**:
   Every request enforces `owner_user_id = principal.userId()` to eliminate Insecure Direct Object References (IDOR).

2. **Idempotency Protection**:
   `cancellation_requests` and `refund_orders` enforce database unique constraints on `(owner_user_id, idempotency_key)`. Repeated requests return the existing receipt without duplicate debit/credits.

3. **Departure Lock**:
   Cancellations attempted after scheduled departure timestamp are rejected at both API and trigger levels.
