# RailConnect Cancellation Architecture (Phase 11A)

Phase 11A establishes a secure, versioned, double-entry financial cancellation and refund management engine for RailConnect.

---

## 1. Core Architecture Principles

1. **Authoritative Server-Side Calculation**:
   Cancellation fees and refund amounts are computed exclusively on the backend using versioned policy snapshots. The frontend is never trusted with financial calculations.

2. **Controlled State Transitions**:
   - `bookings`: `CONFIRMED` $\to$ `CANCELLED`
   - `tickets`: `ISSUED` $\to$ `CANCELLED`
   - `reservation_requests`: `CONFIRMED` $\to$ `RELEASED`
   - `refund_orders`: `CALCULATED` $\to$ `REQUESTED` $\to$ `PROCESSING` $\to$ `SUCCEEDED`

3. **Safe Inventory Release**:
   Only physical berth segment claims belonging to the cancelled passengers are deleted and marked `RELEASED` in `reservation_allocations`. Retained passengers' seats are never disturbed.

4. **Independent Financial Abstraction**:
   RailConnect communicates with Dhruva Bank via server-to-server REST calls (`POST /api/v1/merchant/refunds`). It never touches bank tables directly or bypasses double-entry validation.
