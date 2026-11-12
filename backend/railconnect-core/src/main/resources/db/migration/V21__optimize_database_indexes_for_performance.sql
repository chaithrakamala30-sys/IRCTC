-- ============================================================================
-- RailConnect — Phase 18: Performance Engineering & Query Optimization
-- Migration: V21__optimize_database_indexes_for_performance.sql
-- ============================================================================

-- 1. Optimize Bookings Queries (by owner and status)
CREATE INDEX IF NOT EXISTS idx_bookings_owner_created 
    ON bookings(owner_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bookings_status_created 
    ON bookings(status, created_at DESC);

-- 2. Optimize Seat Inventory & Overlapping Segment Scans
CREATE INDEX IF NOT EXISTS idx_inventory_berths_run_coach 
    ON inventory_berths(train_run_id, inventory_coach_id, berth_number);

-- 3. Optimize Payment Lookups & Reconciliation
CREATE INDEX IF NOT EXISTS idx_payment_orders_booking_status 
    ON payment_orders(booking_id, payment_status);

CREATE INDEX IF NOT EXISTS idx_payment_orders_provider_status 
    ON payment_orders(provider_code, payment_status);

CREATE INDEX IF NOT EXISTS idx_refund_orders_payment_status 
    ON refund_orders(payment_order_id, refund_status);

-- 4. Optimize Train Runs & Journey Schedules
CREATE INDEX IF NOT EXISTS idx_inventory_train_runs_lookup 
    ON inventory_train_runs(train_id, service_date, status);

-- 5. Optimize Support Ticket Queues & SLA Breach Tracking
CREATE INDEX IF NOT EXISTS idx_support_tickets_queue 
    ON support_tickets(status, priority);

CREATE INDEX IF NOT EXISTS idx_support_tickets_passenger 
    ON support_tickets(passenger_user_id, created_at DESC);
