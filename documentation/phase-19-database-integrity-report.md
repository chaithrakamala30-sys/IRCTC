# Phase 19: Database Integrity & Invariant Report

## 1. Relational Invariants Verified
- **Unique Identities**: `users(email)` and `users(mobile_number)` enforce strict uniqueness.
- **PNR Invariant**: `bookings(pnr_number)` enforces unique alphanumeric PNR generation.
- **Seat Inventory Invariant**: `seat_inventory(train_run_id, coach_code, berth_number, segment_index)` prevents duplicate or overlapping seat allocation.
- **Maker-Checker Invariant**: `admin_approval_requests` constraint `chk_no_self_approval` blocks `requested_by = reviewed_by` on approved requests.
- **Confidential Internal Notes**: `support_ticket_messages` enforces boolean isolation on passenger queries.

## 2. Migration Consistency
- Migrations `V1` through `V21` apply cleanly in sequential order without errors or foreign key violations.
