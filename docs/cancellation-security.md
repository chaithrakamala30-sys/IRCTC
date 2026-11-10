# Cancellation & Refund Security Controls

This document details the end-to-end security safeguards protecting RailConnect's cancellation workflow and its integration with Dhruva Bank's merchant refund system.

---

## 1. Authentication & Integrity
1. **Server-to-Server Only**:
   - The Dhruva Bank Refund API is strictly accessible via server-side merchant credentials (`x-merchant-key` + `x-merchant-secret` or HMAC-SHA256 signature).
   - Passenger browsers or frontend clients are never permitted to communicate directly with Dhruva Bank refund endpoints or sign refund payloads.
2. **Replay & Timestamp Protection**:
   - For HMAC-authenticated requests, request timestamps must fall within a 5-minute freshness window ($\pm 300\text{ seconds}$).
   - RailConnect cancellation submissions require unique `idempotency_key` headers per booking cancellation attempt.
3. **Strict Merchant Scoping**:
   - RailConnect only requests refunds against payment intents created under its registered merchant ID (`x-merchant-key`).
   - Cross-merchant or cross-tenant refund attempts are rejected at the gateway with `403 Forbidden` / `404 Not Found`.

---

## 2. Authorization & Ownership (Anti-IDOR)
1. **Ownership Validation**:
   - Every passenger cancellation quote and execution request strictly verifies `owner_user_id = principal.userId()` against the reservation.
   - Staff and administrators can only execute reconciliation actions with granular RBAC permissions (`cancellation:refunds:manage`, `cancellation:refunds:read`).
2. **Departure Lock**:
   - Cancellations attempted after the train's scheduled departure timestamp are rejected at both API, service layer, and PostgreSQL trigger levels.

---

## 3. Ledger Invariants & Anti-Fraud
1. **No Open-Ended Credits (Ceiling Invariant)**:
   - For any payment intent $P$, cumulative refunds must never exceed the original successful payment amount:
     $$\sum_{i=1}^{n} \text{Refund}_i \le \text{PaymentAmount}(P)$$
2. **Fixed Recipient Guarantee**:
   - Funds can only be credited to the original `payer_account_id` captured at payment intent creation.
   - RailConnect cannot redirect refund proceeds to any alternative or third-party bank accounts.
3. **Database Constraints & Double-Credit Prevention**:
   - Unique constraints on `(merchant_id, idempotency_key)` and `(merchant_id, merchant_refund_ref)` prevent duplicate execution at the database level.
   - RailConnect's `cancellation_requests` and `refund_orders` enforce unique constraints on `(booking_id, idempotency_key)`.
4. **Audit Logging & Verification**:
   - All refund events, state changes, and staff reconciliation queries are logged immutably in `cancellation_audit_logs` and Dhruva Bank's `merchant_audit_logs`.
   - Post-execution verification via `POST /api/v1/merchant/refunds/{refundId}/verify` checks that both debit and credit ledger rows match and the transaction is mathematically balanced.


