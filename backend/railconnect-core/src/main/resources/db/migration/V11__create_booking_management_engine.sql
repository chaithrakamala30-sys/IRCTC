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
           AND NEW.status IN ('ALLOCATED_PENDING_BOOKING', 'CONFIRMED')
           AND OLD.booking_correlation_id IS NULL
           AND NEW.booking_correlation_id IS NOT NULL
       ) THEN
        RAISE EXCEPTION 'Booking correlation can only be set for a pending allocation';
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
    ) THEN
        RAISE EXCEPTION 'Invalid reservation request state transition: % -> %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE UNIQUE INDEX reservation_requests_booking_correlation_unique
    ON reservation_requests(booking_correlation_id)
    WHERE booking_correlation_id IS NOT NULL;

ALTER TABLE reservation_allocations
    ADD CONSTRAINT reservation_allocations_id_request_unique
        UNIQUE (reservation_allocation_id, reservation_request_id);

ALTER TABLE reservation_audit_log
    DROP CONSTRAINT reservation_audit_event_type_check;

ALTER TABLE reservation_audit_log
    ADD CONSTRAINT reservation_audit_event_type_check CHECK (
        event_type IN ('REQUEST_CREATED', 'ALLOCATION_CREATED', 'ALLOCATION_FAILED',
                       'ALLOCATION_CONFIRMED', 'ALLOCATION_RELEASED', 'ALLOCATION_EXPIRED',
                       'BOOKING_ATTACHED')
    );

CREATE TABLE bookings (
    booking_id UUID PRIMARY KEY,
    booking_reference VARCHAR(24) NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    journey_date DATE NOT NULL,
    from_station_id UUID NOT NULL REFERENCES stations(station_id),
    to_station_id UUID NOT NULL REFERENCES stations(station_id),
    coach_class_code VARCHAR(32) NOT NULL,
    fare_quote_id UUID NOT NULL UNIQUE REFERENCES fare_quotes(fare_quote_id),
    reservation_request_id UUID NOT NULL UNIQUE REFERENCES reservation_requests(reservation_request_id),
    status VARCHAR(24) NOT NULL DEFAULT 'AWAITING_PAYMENT',
    total_payable NUMERIC(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    payment_deadline TIMESTAMPTZ NOT NULL,
    train_snapshot JSONB NOT NULL,
    journey_snapshot JSONB NOT NULL,
    fare_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bookings_status_check CHECK (status IN ('AWAITING_PAYMENT', 'EXPIRED')),
    CONSTRAINT bookings_amount_check CHECK (total_payable >= 0),
    CONSTRAINT bookings_currency_check CHECK (currency = 'INR'),
    CONSTRAINT bookings_journey_check CHECK (from_station_id <> to_station_id),
    CONSTRAINT bookings_deadline_check CHECK (payment_deadline > created_at),
    CONSTRAINT bookings_reference_format_check
        CHECK (booking_reference ~ '^RC-[23456789A-HJ-NP-Z]{12}$'),
    CONSTRAINT bookings_id_request_unique UNIQUE (booking_id, reservation_request_id),
    CONSTRAINT bookings_snapshot_object_check CHECK (
        jsonb_typeof(train_snapshot) = 'object'
        AND jsonb_typeof(journey_snapshot) = 'object'
        AND jsonb_typeof(fare_snapshot) = 'object'
    )
);

CREATE INDEX bookings_owner_history_idx
    ON bookings(owner_user_id, created_at DESC, booking_id);
CREATE INDEX bookings_owner_status_history_idx
    ON bookings(owner_user_id, status, created_at DESC, booking_id);
CREATE INDEX bookings_expiration_idx
    ON bookings(payment_deadline, booking_id)
    WHERE status = 'AWAITING_PAYMENT';

CREATE TABLE booking_passengers (
    booking_passenger_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    passenger_number SMALLINT NOT NULL,
    reservation_passenger_id UUID NOT NULL UNIQUE REFERENCES reservation_passengers(reservation_passenger_id),
    reservation_allocation_id UUID NOT NULL UNIQUE REFERENCES reservation_allocations(reservation_allocation_id),
    reservation_request_id UUID NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    coach_number VARCHAR(20) NOT NULL,
    berth_number VARCHAR(20) NOT NULL,
    berth_type VARCHAR(24) NOT NULL,
    fare_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT booking_passenger_number_unique UNIQUE (booking_id, passenger_number),
    CONSTRAINT booking_passenger_booking_request_fk
        FOREIGN KEY (booking_id, reservation_request_id)
        REFERENCES bookings(booking_id, reservation_request_id),
    CONSTRAINT booking_passenger_passenger_request_fk
        FOREIGN KEY (reservation_passenger_id, reservation_request_id)
        REFERENCES reservation_passengers(reservation_passenger_id, reservation_request_id),
    CONSTRAINT booking_passenger_allocation_request_fk
        FOREIGN KEY (reservation_allocation_id, reservation_request_id)
        REFERENCES reservation_allocations(reservation_allocation_id, reservation_request_id),
    CONSTRAINT booking_passenger_number_check CHECK (passenger_number > 0),
    CONSTRAINT booking_passenger_name_check CHECK (length(btrim(display_name)) BETWEEN 1 AND 120),
    CONSTRAINT booking_passenger_fare_object_check CHECK (jsonb_typeof(fare_snapshot) = 'object')
);

CREATE INDEX booking_passengers_booking_idx
    ON booking_passengers(booking_id, passenger_number);

CREATE TABLE booking_status_history (
    booking_status_history_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT booking_status_history_status_check CHECK (
        (previous_status IS NULL OR previous_status IN ('AWAITING_PAYMENT', 'EXPIRED'))
        AND new_status IN ('AWAITING_PAYMENT', 'EXPIRED')
    )
);

CREATE INDEX booking_status_history_booking_idx
    ON booking_status_history(booking_id, occurred_at, booking_status_history_id);

CREATE TABLE booking_idempotency_records (
    booking_idempotency_record_id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    booking_id UUID REFERENCES bookings(booking_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT booking_idempotency_owner_key_unique UNIQUE(owner_user_id, idempotency_key),
    CONSTRAINT booking_idempotency_key_check CHECK (length(btrim(idempotency_key)) BETWEEN 1 AND 100)
);

CREATE INDEX booking_idempotency_booking_idx
    ON booking_idempotency_records(booking_id)
    WHERE booking_id IS NOT NULL;

CREATE TABLE booking_request_rate_limits (
    owner_user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    CONSTRAINT booking_request_rate_count_check CHECK (request_count > 0)
);

CREATE TABLE booking_audit_log (
    booking_audit_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    actor_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(32) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT booking_audit_event_type_check CHECK (
        event_type IN ('BOOKING_CREATED', 'BOOKING_EXPIRED', 'ALLOCATION_RELEASE_RETRY')
    ),
    CONSTRAINT booking_audit_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX booking_audit_booking_idx
    ON booking_audit_log(booking_id, occurred_at DESC);

CREATE FUNCTION prevent_booking_snapshot_mutation() RETURNS TRIGGER AS $$
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
       AND NOT (OLD.status = 'AWAITING_PAYMENT' AND NEW.status = 'EXPIRED') THEN
        RAISE EXCEPTION 'Invalid booking state transition: % -> %', OLD.status, NEW.status;
    END IF;
    IF NEW.status = 'EXPIRED' AND OLD.status <> 'EXPIRED'
       AND OLD.payment_deadline > CURRENT_TIMESTAMP THEN
        RAISE EXCEPTION 'A booking cannot expire before its payment deadline';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER bookings_snapshot_immutable
    BEFORE UPDATE OR DELETE ON bookings
    FOR EACH ROW EXECUTE FUNCTION prevent_booking_snapshot_mutation();

CREATE FUNCTION prevent_booking_passenger_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Booking passenger snapshots are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER booking_passengers_immutable
    BEFORE UPDATE OR DELETE ON booking_passengers
    FOR EACH ROW EXECUTE FUNCTION prevent_booking_passenger_mutation();

CREATE FUNCTION prevent_booking_history_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Booking history and audit records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER booking_status_history_append_only
    BEFORE UPDATE OR DELETE ON booking_status_history
    FOR EACH ROW EXECUTE FUNCTION prevent_booking_history_mutation();

CREATE TRIGGER booking_audit_append_only
    BEFORE UPDATE OR DELETE ON booking_audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_booking_history_mutation();

CREATE FUNCTION guard_booking_idempotency_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Booking idempotency records cannot be deleted';
    END IF;
    IF NEW.booking_idempotency_record_id <> OLD.booking_idempotency_record_id
       OR NEW.owner_user_id <> OLD.owner_user_id
       OR NEW.idempotency_key <> OLD.idempotency_key
       OR NEW.request_fingerprint <> OLD.request_fingerprint
       OR NEW.created_at <> OLD.created_at
       OR (OLD.booking_id IS NOT NULL AND NEW.booking_id IS DISTINCT FROM OLD.booking_id)
       OR (OLD.booking_id IS NULL AND NEW.booking_id IS NULL) THEN
        RAISE EXCEPTION 'Booking idempotency record is immutable after completion';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER booking_idempotency_immutable
    BEFORE UPDATE OR DELETE ON booking_idempotency_records
    FOR EACH ROW EXECUTE FUNCTION guard_booking_idempotency_mutation();

INSERT INTO permissions(permission_code, description) VALUES
    ('bookings:own:create', 'Create a booking from own active fare quote and provisional allocation'),
    ('bookings:own:read', 'Read own booking details and history')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code)
SELECT 'PASSENGER', permission_code
FROM permissions
WHERE permission_code IN ('bookings:own:create', 'bookings:own:read')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code)
SELECT 'SUPER_ADMIN', permission_code
FROM permissions
WHERE permission_code IN ('bookings:own:create', 'bookings:own:read')
ON CONFLICT DO NOTHING;
