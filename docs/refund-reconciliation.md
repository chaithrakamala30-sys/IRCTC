# Refund Reconciliation & Audit

---

## Reconciliation Engine
1. In the event of network disruption or gateway timeouts, refund orders enter `REQUIRES_RECONCILIATION`.
2. Authorized staff (`refunds:admin:reconcile`) can trigger `/api/v1/admin/refunds/{refundId}/reconcile`.
3. The server invokes Dhruva Bank's `/verify` endpoint to check double-entry ledger settlement.
4. When verified by the bank, the refund status transitions cleanly to `SUCCEEDED`.
