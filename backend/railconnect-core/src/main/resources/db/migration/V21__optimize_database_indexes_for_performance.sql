-- ============================================================================
-- RailConnect — Phase 18: Performance Engineering & Query Optimization
-- Migration: V21__optimize_database_indexes_for_performance.sql
-- ============================================================================

-- 1. Optimize Bookings & PNR Queries
CREATE INDEX IF NOT EXISTS idx_bookings_user_created 
    ON bookings(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_bookings_status_created 
    ON bookings(status, created_at DESC);

-- 2. Optimize Seat Inventory & Overlapping Segment Scans
CREATE INDEX IF NOT EXISTS idx_seat_inventory_run_coach 
    ON seat_inventory(train_run_id, coach_code, berth_number);

-- 3. Optimize Payment Lookups & Reconciliation
CREATE INDEX IF NOT EXISTS idx_payments_booking_status 
    ON payments(booking_id, status);

CREATE INDEX IF NOT EXISTS idx_payments_gateway_status 
    ON payments(gateway_name, status);

CREATE INDEX IF NOT EXISTS idx_refund_requests_payment_status 
    ON refund_requests(payment_id, status);

-- 4. Optimize Train Runs & Journey Schedules
CREATE INDEX IF NOT EXISTS idx_train_runs_lookup 
    ON train_runs(train_number, run_date, status);

-- 5. Optimize Support Ticket Queues & SLA Breach Tracking
CREATE INDEX IF NOT EXISTS idx_support_tickets_queue 
    ON support_tickets(status, priority, sla_breached);

CREATE INDEX IF NOT EXISTS idx_support_tickets_customer 
    ON support_tickets(customer_id, created_at DESC);
