# Production Database Deployment & Migration Audit

## 1. Schema Migration Verification
- Migrations `V1` through `V21` applied successfully via Flyway:
  - `V1`–`V4`: Application metadata, IAM, user credentials, authentication event audit logs.
  - `V5`–`V9`: Railway master data, passenger search indexing, fare engine, seat inventory.
  - `V10`–`V14`: Reservation allocation, booking engine, RailConnect Pay scaffolding, verified payments, ticket generation.
  - `V15`–`V18`: Cancellation and refunds, RAC/WL charting, OCC operations, notifications & WebPush.
  - `V19`–`V21`: Customer support & complaint management, Admin Control Centre & Maker-Checker, performance index optimization.

## 2. Invariant & Data Safety
- Unique constraints on `email`, `mobile_number`, `pnr_number`, and `(train_run_id, coach_code, berth_number, segment_index)` are active.
- Maker-checker constraint `chk_no_self_approval` blocks unauthorized self-approval.

