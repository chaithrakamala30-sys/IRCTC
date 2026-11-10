-- ============================================================================
-- Flyway Migration V15: RailConnect Cancellation & Refund Management Engine
-- Phase 11A Core Database Schema
-- ============================================================================

-- 1. Extend bookings status check to support CANCELLED
ALTER TABLE bookings
    DROP CONSTRAINT IF EXISTS bookings_status_check;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
        CHECK (status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED'));

-- 2. Extend booking status history check
ALTER TABLE booking_status_history
    DROP CONSTRAINT IF EXISTS booking_status_history_status_check;

ALTER TABLE booking_status_history
    ADD CONSTRAINT booking_status_history_status_check CHECK (
        (previous_status IS NULL OR previous_status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED'))
        AND new_status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED')
    );

-- 3. Extend booking audit log event types
ALTER TABLE booking_audit_log
    DROP CONSTRAINT IF EXISTS booking_audit_event_type_check;

ALTER TABLE booking_audit_log
    ADD CONSTRAINT booking_audit_event_type_check CHECK (
        event_type IN (
            'BOOKING_CREATED', 'BOOKING_EXPIRED', 'BOOKING_PAYMENT_CONFIRMED',
            'ALLOCATION_RELEASE_RETRY', 'BOOKING_CANCELLED'
        )
    );

-- 4. Update prevent_booking_snapshot_mutation function to permit transition to CANCELLED
CREATE OR REPLACE FUNCTION prevent_booking_snapshot_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Bookings cannot be deleted';
    END IF;
    IF NEW.booking_id <> OLD.booking_id
       OR NEW.booking_reference <> OLD.booking_reference
       OR NEW.owner_user_id <> OLD.owner_user_id
       OR NEW.train_run_id <> OLD.train_run_id
       OR NEW.journey_date <> OLD.journey_date
       OR NEW.from_station_id <> OLD.from_station_id
       OR NEW.to_station_id <> OLD.to_station_id
       OR NEW.coach_class_code <> OLD.coach_class_code
       OR NEW.fare_quote_id <> OLD.fare_quote_id
       OR NEW.reservation_request_id <> OLD.reservation_request_id
       OR NEW.total_payable <> OLD.total_payable
       OR NEW.currency <> OLD.currency
       OR NEW.payment_deadline <> OLD.payment_deadline
       OR NEW.train_snapshot <> OLD.train_snapshot
       OR NEW.journey_snapshot <> OLD.journey_snapshot
       OR NEW.fare_snapshot <> OLD.fare_snapshot
       OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Booking details and snapshots are immutable';
    END IF;
    IF NEW.status <> OLD.status
       AND NOT (
           (OLD.status = 'AWAITING_PAYMENT' AND NEW.status IN ('EXPIRED', 'CONFIRMED'))
           OR (OLD.status = 'CONFIRMED' AND NEW.status = 'CANCELLED')
       ) THEN
        RAISE EXCEPTION 'Invalid booking state transition: % -> %', OLD.status, NEW.status;
    END IF;
    IF NEW.status = 'EXPIRED' AND OLD.status <> 'EXPIRED'
       AND OLD.payment_deadline > CURRENT_TIMESTAMP THEN
        RAISE EXCEPTION 'A booking cannot expire before its payment deadline';
    END IF;
    IF NEW.status = 'CONFIRMED' AND OLD.status <> 'CONFIRMED'
       AND OLD.payment_deadline <= CURRENT_TIMESTAMP THEN
        RAISE EXCEPTION 'A booking cannot be confirmed after its payment deadline';
    END IF;
    IF NEW.status = 'CONFIRMED' AND OLD.status <> 'CONFIRMED'
       AND NOT EXISTS (
           SELECT 1
           FROM payment_orders payment
           JOIN payment_status_history history
             ON history.payment_order_id = payment.payment_order_id
           WHERE payment.booking_id = NEW.booking_id
             AND payment.payment_status = 'SUCCEEDED'
             AND history.new_status = 'SUCCEEDED'
             AND history.source IN ('STATUS_QUERY', 'VERIFIED_WEBHOOK', 'RECONCILIATION')
       ) THEN
        RAISE EXCEPTION 'Booking confirmation requires verified payment evidence';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 5. Extend tickets status check to support CANCELLED
ALTER TABLE tickets
    DROP CONSTRAINT IF EXISTS tickets_status_check;

ALTER TABLE tickets
    ADD CONSTRAINT tickets_status_check
        CHECK (ticket_status IN ('ISSUED', 'CANCELLED'));

-- 6. Extend ticket audit log action check
ALTER TABLE ticket_audit_logs
    DROP CONSTRAINT IF EXISTS ticket_audit_action_check;

ALTER TABLE ticket_audit_logs
    ADD CONSTRAINT ticket_audit_action_check CHECK (
        action IN (
            'TICKET_ISSUED', 'PASSENGER_READ', 'ADMIN_LOOKUP', 'ADMIN_RETRY',
            'OPERATIONS_VERIFY', 'PUBLIC_VERIFY', 'PDF_DOWNLOAD', 'TICKET_CANCELLED'
        )
    );

-- 7. Update reservation request state transition trigger to permit CONFIRMED -> RELEASED on cancellation
CREATE OR REPLACE FUNCTION enforce_reservation_request_state_transition() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Reservation requests cannot be deleted';
    END IF;
    IF NEW.reservation_request_id <> OLD.reservation_request_id
       OR NEW.owner_user_id <> OLD.owner_user_id
       OR NEW.train_run_id <> OLD.train_run_id
       OR NEW.journey_date <> OLD.journey_date
       OR NEW.from_stop_index <> OLD.from_stop_index
       OR NEW.to_stop_index <> OLD.to_stop_index
       OR NEW.coach_class_code <> OLD.coach_class_code
       OR NEW.group_seating_requested <> OLD.group_seating_requested
       OR NEW.idempotency_key <> OLD.idempotency_key
       OR NEW.request_fingerprint <> OLD.request_fingerprint
       OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Reservation request inputs are immutable';
    END IF;

    IF NEW.booking_correlation_id IS DISTINCT FROM OLD.booking_correlation_id
       AND NOT (
           OLD.status = 'ALLOCATED_PENDING_BOOKING'
           AND NEW.status = 'CONFIRMED'
           AND OLD.booking_correlation_id IS NULL
           AND NEW.booking_correlation_id IS NOT NULL
       ) THEN
        RAISE EXCEPTION 'Booking correlation can only be set while confirming an allocation';
    END IF;
    IF NEW.allocation_policy_version IS DISTINCT FROM OLD.allocation_policy_version
       AND NOT (
           OLD.status = 'REQUESTED'
           AND NEW.status = 'ALLOCATED_PENDING_BOOKING'
           AND OLD.allocation_policy_version IS NULL
           AND NEW.allocation_policy_version IS NOT NULL
       ) THEN
        RAISE EXCEPTION 'Allocation policy version can only be recorded during allocation';
    END IF;

    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;

    IF NOT (
        (OLD.status = 'REQUESTED' AND NEW.status IN
            ('ALLOCATED_PENDING_BOOKING', 'FAILED', 'RELEASED', 'EXPIRED'))
        OR (OLD.status = 'ALLOCATED_PENDING_BOOKING' AND NEW.status IN
            ('CONFIRMED', 'RELEASED', 'EXPIRED'))
        OR (OLD.status = 'CONFIRMED' AND NEW.status = 'RELEASED')
    ) THEN
        RAISE EXCEPTION 'Invalid reservation request state transition: % -> %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 8. Update reservation allocation state transition trigger to permit CONFIRMED -> RELEASED on cancellation
CREATE OR REPLACE FUNCTION enforce_reservation_allocation_state_transition() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Reservation allocations cannot be deleted';
    END IF;
    IF NEW.reservation_allocation_id <> OLD.reservation_allocation_id
       OR NEW.reservation_request_id <> OLD.reservation_request_id
       OR NEW.reservation_passenger_id <> OLD.reservation_passenger_id
       OR NEW.train_run_id <> OLD.train_run_id
       OR NEW.inventory_berth_id <> OLD.inventory_berth_id
       OR NEW.inventory_hold_id <> OLD.inventory_hold_id
       OR NEW.from_stop_index <> OLD.from_stop_index
       OR NEW.to_stop_index <> OLD.to_stop_index
       OR NEW.coach_class_code <> OLD.coach_class_code
       OR NEW.preferred_berth_type IS DISTINCT FROM OLD.preferred_berth_type
       OR NEW.preference_result <> OLD.preference_result
       OR NEW.allocation_policy_version <> OLD.allocation_policy_version
       OR NEW.allocation_score <> OLD.allocation_score
       OR NEW.allocated_at <> OLD.allocated_at THEN
        RAISE EXCEPTION 'Reservation allocation details are immutable';
    END IF;

    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;

    IF NOT (
        (OLD.status = 'ALLOCATED_PENDING_BOOKING' AND NEW.status IN ('CONFIRMED', 'RELEASED', 'EXPIRED'))
        OR (OLD.status = 'CONFIRMED' AND NEW.status = 'RELEASED')
    ) THEN
        RAISE EXCEPTION 'Invalid reservation allocation state transition: % -> %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 9. Cancellation Policy Versions
-- ============================================================================
CREATE TABLE cancellation_policy_versions (
    policy_version_id UUID PRIMARY KEY,
    version_number INTEGER NOT NULL UNIQUE,
    policy_name VARCHAR(100) NOT NULL,
    description TEXT,
    policy_rules JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    published_by UUID REFERENCES users(user_id),
    CONSTRAINT cancellation_policy_status_check
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT cancellation_policy_rules_array_check
        CHECK (jsonb_typeof(policy_rules) = 'array')
);

CREATE UNIQUE INDEX cancellation_policy_published_idx
    ON cancellation_policy_versions(status)
    WHERE status = 'PUBLISHED';

-- Seed initial RailConnect Cancellation Policy Version 1
INSERT INTO cancellation_policy_versions (
    policy_version_id, version_number, policy_name, description, policy_rules, status, published_at
) VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    1,
    'RailConnect Standard Cancellation Policy v1',
    'Standard tiered cancellation rules based on departure timeframe and travel class.',
    '[
        {
            "tierCode": "MORE_THAN_48_HOURS",
            "minHoursBeforeDeparture": 48,
            "deductionType": "FIXED_PER_PASSENGER",
            "classDeductions": {
                "1A": 240.00,
                "EC": 200.00,
                "2A": 180.00,
                "3A": 140.00,
                "CC": 120.00,
                "SL": 80.00,
                "2S": 40.00
            },
            "defaultFixedAmount": 100.00
        },
        {
            "tierCode": "BETWEEN_12_AND_48_HOURS",
            "minHoursBeforeDeparture": 12,
            "maxHoursBeforeDeparture": 48,
            "deductionType": "PERCENTAGE_WITH_MINIMUM",
            "percentage": 25.0,
            "classMinimums": {
                "1A": 240.00,
                "EC": 200.00,
                "2A": 180.00,
                "3A": 140.00,
                "CC": 120.00,
                "SL": 80.00,
                "2S": 40.00
            },
            "defaultMinimum": 100.00
        },
        {
            "tierCode": "BETWEEN_4_AND_12_HOURS",
            "minHoursBeforeDeparture": 4,
            "maxHoursBeforeDeparture": 12,
            "deductionType": "PERCENTAGE",
            "percentage": 50.0
        },
        {
            "tierCode": "LESS_THAN_4_HOURS",
            "minHoursBeforeDeparture": 0,
            "maxHoursBeforeDeparture": 4,
            "deductionType": "NON_REFUNDABLE",
            "percentage": 100.0
        }
    ]'::jsonb,
    'PUBLISHED',
    CURRENT_TIMESTAMP
) ON CONFLICT (version_number) DO NOTHING;

-- ============================================================================
-- 10. Cancellation Requests
-- ============================================================================
CREATE TABLE cancellation_requests (
    cancellation_id UUID PRIMARY KEY,
    cancellation_reference VARCHAR(32) NOT NULL UNIQUE,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    ticket_id UUID NOT NULL REFERENCES tickets(ticket_id),
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    cancellation_type VARCHAR(16) NOT NULL DEFAULT 'FULL',
    cancellation_status VARCHAR(24) NOT NULL DEFAULT 'CONFIRMED',
    policy_version_id UUID NOT NULL REFERENCES cancellation_policy_versions(policy_version_id),
    hours_before_departure NUMERIC(8, 2) NOT NULL,
    departure_time TIMESTAMPTZ NOT NULL,
    total_original_amount NUMERIC(14, 2) NOT NULL,
    total_cancellation_fee NUMERIC(14, 2) NOT NULL,
    total_refundable_amount NUMERIC(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    reason TEXT,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cancellation_reference_format_check
        CHECK (cancellation_reference ~ '^CAN-[0-9]{10}$'),
    CONSTRAINT cancellation_type_check
        CHECK (cancellation_type IN ('FULL', 'PARTIAL')),
    CONSTRAINT cancellation_status_check
        CHECK (cancellation_status IN ('CONFIRMED', 'REJECTED')),
    CONSTRAINT cancellation_amounts_check
        CHECK (total_original_amount >= 0 AND total_cancellation_fee >= 0 AND total_refundable_amount >= 0),
    CONSTRAINT cancellation_math_check
        CHECK (total_refundable_amount + total_cancellation_fee = total_original_amount),
    CONSTRAINT cancellation_idempotency_unique
        UNIQUE (owner_user_id, idempotency_key)
);

CREATE INDEX cancellation_requests_owner_idx
    ON cancellation_requests(owner_user_id, created_at DESC);
CREATE INDEX cancellation_requests_booking_idx
    ON cancellation_requests(booking_id);
CREATE INDEX cancellation_requests_ticket_idx
    ON cancellation_requests(ticket_id);

-- ============================================================================
-- 11. Cancellation Passengers (for passenger-level tracking)
-- ============================================================================
CREATE TABLE cancellation_passengers (
    cancellation_passenger_id UUID PRIMARY KEY,
    cancellation_id UUID NOT NULL REFERENCES cancellation_requests(cancellation_id) ON DELETE CASCADE,
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id),
    ticket_passenger_id UUID NOT NULL REFERENCES ticket_passengers(ticket_passenger_id),
    reservation_allocation_id UUID NOT NULL,
    passenger_name VARCHAR(120) NOT NULL,
    coach_number VARCHAR(20) NOT NULL,
    berth_number VARCHAR(20) NOT NULL,
    fare_paid NUMERIC(14, 2) NOT NULL,
    cancellation_fee NUMERIC(14, 2) NOT NULL,
    refundable_amount NUMERIC(14, 2) NOT NULL,
    applied_tier_code VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cancellation_pax_amounts_check
        CHECK (fare_paid >= 0 AND cancellation_fee >= 0 AND refundable_amount >= 0),
    CONSTRAINT cancellation_pax_math_check
        CHECK (refundable_amount + cancellation_fee = fare_paid)
);

CREATE INDEX cancellation_passengers_cancellation_idx
    ON cancellation_passengers(cancellation_id);
CREATE INDEX cancellation_passengers_booking_pax_idx
    ON cancellation_passengers(booking_passenger_id);

-- ============================================================================
-- 12. Refund Orders (RailConnect Pay Merchant Refund records)
-- ============================================================================
CREATE TABLE refund_orders (
    refund_order_id UUID PRIMARY KEY,
    cancellation_id UUID NOT NULL UNIQUE REFERENCES cancellation_requests(cancellation_id),
    payment_order_id UUID NOT NULL REFERENCES payment_orders(payment_order_id),
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    merchant_refund_ref VARCHAR(80) NOT NULL UNIQUE,
    provider_code VARCHAR(32) NOT NULL DEFAULT 'DHRUVA_BANK',
    provider_refund_id VARCHAR(128),
    payment_intent_id VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    refund_status VARCHAR(32) NOT NULL DEFAULT 'CALCULATED',
    reason TEXT,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    failure_message TEXT,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT refund_orders_status_check CHECK (
        refund_status IN (
            'NOT_ELIGIBLE', 'CALCULATED', 'REQUESTED', 'PROCESSING',
            'SUCCEEDED', 'FAILED', 'REQUIRES_RECONCILIATION', 'REVERSED'
        )
    ),
    CONSTRAINT refund_orders_amount_check CHECK (amount >= 0 AND amount_minor >= 0),
    CONSTRAINT refund_orders_currency_check CHECK (currency = 'INR')
);

CREATE INDEX refund_orders_booking_idx ON refund_orders(booking_id);
CREATE INDEX refund_orders_owner_idx ON refund_orders(owner_user_id, created_at DESC);
CREATE INDEX refund_orders_status_idx ON refund_orders(refund_status);

-- ============================================================================
-- 13. Refund Attempts Audit
-- ============================================================================
CREATE TABLE refund_attempts (
    refund_attempt_id UUID PRIMARY KEY,
    refund_order_id UUID NOT NULL REFERENCES refund_orders(refund_order_id),
    attempt_number INTEGER NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_payload JSONB NOT NULL,
    response_code INTEGER,
    response_payload JSONB,
    status VARCHAR(32) NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms BIGINT
);

CREATE INDEX refund_attempts_order_idx ON refund_attempts(refund_order_id, attempt_number);

-- ============================================================================
-- 14. Refund Status History
-- ============================================================================
CREATE TABLE refund_status_history (
    refund_status_history_id UUID PRIMARY KEY,
    refund_order_id UUID NOT NULL REFERENCES refund_orders(refund_order_id),
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT refund_history_source_check CHECK (
        source IN ('SYSTEM', 'PROVIDER_RESPONSE', 'VERIFIED_WEBHOOK', 'STATUS_QUERY', 'RECONCILIATION')
    )
);

CREATE INDEX refund_status_history_order_idx ON refund_status_history(refund_order_id, occurred_at);

-- ============================================================================
-- 15. Inventory Release History
-- ============================================================================
CREATE TABLE inventory_release_history (
    release_history_id UUID PRIMARY KEY,
    cancellation_id UUID NOT NULL REFERENCES cancellation_requests(cancellation_id),
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    inventory_berth_id UUID NOT NULL REFERENCES inventory_berths(inventory_berth_id),
    reservation_allocation_id UUID NOT NULL,
    from_stop_index SMALLINT NOT NULL,
    to_stop_index SMALLINT NOT NULL,
    segments_released INTEGER NOT NULL,
    released_by UUID REFERENCES users(user_id),
    released_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_release_segments_check CHECK (segments_released > 0)
);

CREATE INDEX inventory_release_run_idx ON inventory_release_history(train_run_id, released_at DESC);

-- ============================================================================
-- 16. Cancellation Audit Logs
-- ============================================================================
CREATE TABLE cancellation_audit_logs (
    cancellation_audit_id UUID PRIMARY KEY,
    cancellation_id UUID REFERENCES cancellation_requests(cancellation_id),
    refund_order_id UUID REFERENCES refund_orders(refund_order_id),
    actor_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(40) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT cancellation_audit_details_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX cancellation_audit_cancellation_idx
    ON cancellation_audit_logs(cancellation_id, occurred_at DESC);
CREATE INDEX cancellation_audit_actor_idx
    ON cancellation_audit_logs(actor_user_id, occurred_at DESC);

-- ============================================================================
-- 17. Permissions and Role Grants
-- ============================================================================
INSERT INTO permissions (permission_code, description) VALUES
    ('cancellations:own:create', 'Request ticket cancellation and refund for own bookings'),
    ('cancellations:own:read', 'View own cancellation history and refund receipts'),
    ('cancellations:admin:read', 'Review cancellation requests and refund audit records'),
    ('cancellations:admin:manage', 'Manage versioned cancellation policies'),
    ('refunds:admin:reconcile', 'Perform authoritative refund verification and reconciliation')
ON CONFLICT (permission_code) DO NOTHING;

-- Grant passenger permissions
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'PASSENGER', permission_code
FROM permissions
WHERE permission_code IN ('cancellations:own:create', 'cancellations:own:read')
ON CONFLICT DO NOTHING;

-- Grant admin permissions
INSERT INTO role_permissions (role_code, permission_code)
SELECT roles.role_code, permissions.permission_code
FROM (VALUES ('SUPER_ADMIN'), ('FINANCE_ADMIN'), ('RAILWAY_ADMIN')) roles(role_code)
CROSS JOIN permissions
WHERE permissions.permission_code IN (
    'cancellations:admin:read', 'cancellations:admin:manage', 'refunds:admin:reconcile'
)
ON CONFLICT DO NOTHING;

