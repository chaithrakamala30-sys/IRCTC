# Refund Management & State Lifecycle

---

## 1. Refund States
- `NOT_ELIGIBLE`: Total cancellation fee equals or exceeds original fare (e.g. cancelled under 4 hours before departure).
- `CALCULATED`: Authoritative refund quote computed.
- `REQUESTED`: Dispatched to Dhruva Bank refund endpoint.
- `PROCESSING`: Dhruva Bank double-entry ledger lock acquired.
- `SUCCEEDED`: Settled and verified against bank statement.
- `FAILED`: Insufficient merchant balance or terminal failure.
- `REQUIRES_RECONCILIATION`: Network timeout requiring staff inspection.

---

## 2. Idempotency & Financial Invariants
- Each refund carries `RC-REF-...` and an explicit `idempotency_key`.
- Double-credit is prevented at the database and bank API levels.
