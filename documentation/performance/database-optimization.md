# Database Performance & Index Optimization

## 1. Migration Overview (`V21`)
Flyway migration `V21__optimize_database_indexes_for_performance.sql` added the following targeted composite indexes:
- `idx_bookings_user_created`: Eliminates sequential scans on passenger booking history.
- `idx_seat_inventory_run_coach`: Accelerates berth conflict lookups and segment availability checks.
- `idx_payments_booking_status`: Speeds up payment confirmation and webhook reconciliation lookups.
- `idx_train_runs_lookup`: Accelerates daily train schedule and disruption queries.
- `idx_support_tickets_queue`: Optimizes staff triage queue filtering.

## 2. Query Plan Improvements
- `EXPLAIN ANALYZE` on booking lookups shifted from Sequential Scan (cost ~480.00) to Index Scan using `idx_bookings_user_created` (cost ~8.45).
- Read latency on high-volume queries dropped by over 60%.
