# Maker-Checker Workflow & Governance Specification

## Objective
To enforce the Four-Eye Principle across all critical commercial, administrative, and configuration operations in RailConnect.

## Workflow Mechanics
1. **Maker (Proposer)**:
   - Any authorized staff member (`SUPER_ADMIN`, `RAILWAY_ADMIN`, `FINANCE_ADMIN`, `CUSTOMER_SUPPORT`) creates a change request with Category, Title, Justification, and JSON Payload.
   - Initial status: `PENDING_APPROVAL`.
2. **Checker (Reviewer)**:
   - An independent authorized reviewer inspects the request queue.
   - **Self-Approval Prevention**: The database constraint `chk_no_self_approval` and backend service rule `req.requestedBy().equals(principal.userId())` strictly reject attempts by the creator to approve their own proposal (`SecurityException`).
3. **Execution**:
   - On approval, the record transitions to `APPROVED` and `applied_at` is set.
   - On rejection, reviewer remarks are captured and status transitions to `REJECTED`.
   - Immutable audit logs record both maker and checker actions.
