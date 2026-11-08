ALTER TABLE inventory_holds
    DROP CONSTRAINT inventory_holds_status_check;

ALTER TABLE inventory_holds
    ADD CONSTRAINT inventory_holds_status_check
        CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'CONSUMED'));

CREATE TABLE reservation_allocation_policies (
    version_number SMALLINT PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    preferred_berth_weight INTEGER NOT NULL,
    same_coach_weight INTEGER NOT NULL,
    nearby_berth_weight INTEGER NOT NULL,
    created_by UUID REFERENCES users(user_id),
    approved_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    CONSTRAINT reservation_allocation_policy_status_check
        CHECK (status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT reservation_allocation_policy_weights_check CHECK (
        preferred_berth_weight BETWEEN 0 AND 10000
        AND same_coach_weight BETWEEN 0 AND 10000
        AND nearby_berth_weight BETWEEN 0 AND 10000
    ),
    CONSTRAINT reservation_allocation_policy_approval_check CHECK (
        (status = 'DRAFT' AND approved_by IS NULL AND approved_at IS NULL)
        OR (status IN ('APPROVED', 'PUBLISHED', 'RETIRED')
            AND ((approved_by IS NOT NULL AND approved_at IS NOT NULL) OR version_number = 1))
    ),
    CONSTRAINT reservation_allocation_policy_publish_check CHECK (
        (status IN ('PUBLISHED', 'RETIRED') AND published_at IS NOT NULL)
        OR status IN ('DRAFT', 'APPROVED')
    )
);

CREATE UNIQUE INDEX reservation_allocation_policy_one_published_idx
    ON reservation_allocation_policies(status)
    WHERE status = 'PUBLISHED';

CREATE FUNCTION enforce_reservation_allocation_policy_transition() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Reservation allocation policies cannot be deleted';
    END IF;
    IF NEW.version_number <> OLD.version_number
       OR NEW.preferred_berth_weight <> OLD.preferred_berth_weight
       OR NEW.same_coach_weight <> OLD.same_coach_weight
       OR NEW.nearby_berth_weight <> OLD.nearby_berth_weight
       OR NEW.created_by IS DISTINCT FROM OLD.created_by
       OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Reservation allocation policy versions are immutable';
    END IF;
    IF NEW.approved_by IS DISTINCT FROM OLD.approved_by
       OR NEW.approved_at IS DISTINCT FROM OLD.approved_at THEN
        IF NOT (
            OLD.status = 'DRAFT' AND NEW.status = 'APPROVED'
            AND NEW.approved_by IS NOT NULL AND NEW.approved_at IS NOT NULL
            AND NEW.approved_by IS DISTINCT FROM OLD.created_by
        ) THEN
            RAISE EXCEPTION 'Policy approval details can only be set during approval';
        END IF;
    END IF;
    IF NEW.published_at IS DISTINCT FROM OLD.published_at
       AND NOT (
           OLD.status = 'APPROVED' AND NEW.status = 'PUBLISHED'
           AND NEW.published_at IS NOT NULL
       ) THEN
        RAISE EXCEPTION 'Policy publish time can only be set during publishing';
    END IF;
    IF NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;
    IF NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'APPROVED'
            AND NEW.approved_by IS NOT NULL AND NEW.approved_at IS NOT NULL
            AND NEW.approved_by IS DISTINCT FROM OLD.created_by)
        OR (OLD.status = 'APPROVED' AND NEW.status = 'PUBLISHED'
            AND NEW.published_at IS NOT NULL)
        OR (OLD.status = 'PUBLISHED' AND NEW.status = 'RETIRED')
    ) THEN
        RAISE EXCEPTION 'Invalid reservation allocation policy transition: % -> %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservation_allocation_policy_transition_guard
    BEFORE UPDATE OR DELETE ON reservation_allocation_policies
    FOR EACH ROW EXECUTE FUNCTION enforce_reservation_allocation_policy_transition();

INSERT INTO reservation_allocation_policies(
    version_number, status, preferred_berth_weight, same_coach_weight, nearby_berth_weight,
    approved_at, published_at
)
VALUES (1, 'PUBLISHED', 100, 25, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE reservation_requests (
    reservation_request_id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    journey_date DATE NOT NULL,
    from_stop_index SMALLINT NOT NULL,
    to_stop_index SMALLINT NOT NULL,
    coach_class_code VARCHAR(32) NOT NULL,
    group_seating_requested BOOLEAN NOT NULL DEFAULT FALSE,
    group_seating_result VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUESTED',
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    failure_code VARCHAR(64),
    failure_reason VARCHAR(300),
    expires_at TIMESTAMPTZ,
    allocation_policy_version SMALLINT REFERENCES reservation_allocation_policies(version_number),
    booking_correlation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_requests_owner_idempotency_unique
        UNIQUE (owner_user_id, idempotency_key),
    CONSTRAINT reservation_requests_run_identity_unique
        UNIQUE (reservation_request_id, train_run_id),
    CONSTRAINT reservation_requests_from_stop_fk
        FOREIGN KEY (train_run_id, from_stop_index)
        REFERENCES inventory_run_stops(train_run_id, stop_index),
    CONSTRAINT reservation_requests_to_stop_fk
        FOREIGN KEY (train_run_id, to_stop_index)
        REFERENCES inventory_run_stops(train_run_id, stop_index),
    CONSTRAINT reservation_requests_status_check CHECK (
        status IN ('REQUESTED', 'ALLOCATED_PENDING_BOOKING', 'CONFIRMED',
                   'RELEASED', 'FAILED', 'EXPIRED')
    ),
    CONSTRAINT reservation_requests_group_result_check CHECK (
        group_seating_result IN ('SATISFIED', 'ALTERNATIVE', 'NOT_REQUESTED')
        AND (group_seating_requested OR group_seating_result = 'NOT_REQUESTED')
    ),
    CONSTRAINT reservation_requests_range_check
        CHECK (from_stop_index >= 0 AND to_stop_index > from_stop_index),
    CONSTRAINT reservation_requests_failure_check CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL AND failure_reason IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL AND failure_reason IS NULL)
    ),
    CONSTRAINT reservation_requests_expiry_check
        CHECK (status <> 'ALLOCATED_PENDING_BOOKING'
            OR (expires_at IS NOT NULL AND allocation_policy_version IS NOT NULL))
);

CREATE INDEX reservation_requests_owner_idx
    ON reservation_requests(owner_user_id, created_at DESC, reservation_request_id);
CREATE INDEX reservation_requests_run_status_idx
    ON reservation_requests(train_run_id, status, created_at DESC);
CREATE INDEX reservation_requests_failure_idx
    ON reservation_requests(created_at DESC, reservation_request_id)
    WHERE status = 'FAILED';

CREATE TABLE reservation_passengers (
    reservation_passenger_id UUID PRIMARY KEY,
    reservation_request_id UUID NOT NULL REFERENCES reservation_requests(reservation_request_id),
    passenger_number SMALLINT NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    preferred_berth_type VARCHAR(24),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_passengers_request_number_unique
        UNIQUE (reservation_request_id, passenger_number),
    CONSTRAINT reservation_passengers_id_request_unique
        UNIQUE (reservation_passenger_id, reservation_request_id),
    CONSTRAINT reservation_passengers_number_check CHECK (passenger_number > 0),
    CONSTRAINT reservation_passengers_name_check
        CHECK (length(btrim(display_name)) BETWEEN 1 AND 120),
    CONSTRAINT reservation_passengers_preference_check CHECK (
        preferred_berth_type IS NULL OR preferred_berth_type IN
            ('LOWER_BERTH', 'MIDDLE_BERTH', 'UPPER_BERTH', 'SIDE_LOWER_BERTH', 'SIDE_UPPER_BERTH')
    )
);

CREATE INDEX reservation_passengers_request_idx
    ON reservation_passengers(reservation_request_id, passenger_number);

CREATE TABLE reservation_request_holds (
    reservation_request_id UUID NOT NULL REFERENCES reservation_requests(reservation_request_id),
    inventory_hold_id UUID NOT NULL UNIQUE REFERENCES inventory_holds(inventory_hold_id),
    PRIMARY KEY (reservation_request_id, inventory_hold_id)
);

CREATE INDEX reservation_request_holds_hold_idx
    ON reservation_request_holds(inventory_hold_id, reservation_request_id);

CREATE FUNCTION guard_reservation_request_details_mutation() RETURNS TRIGGER AS $$
DECLARE
    request_status VARCHAR(32);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT status INTO request_status
        FROM reservation_requests
        WHERE reservation_request_id = NEW.reservation_request_id;
        IF request_status = 'REQUESTED' THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'Reservation request details are immutable after request creation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservation_passengers_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON reservation_passengers
    FOR EACH ROW EXECUTE FUNCTION guard_reservation_request_details_mutation();

CREATE TRIGGER reservation_request_holds_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON reservation_request_holds
    FOR EACH ROW EXECUTE FUNCTION guard_reservation_request_details_mutation();

CREATE TABLE reservation_allocations (
    reservation_allocation_id UUID PRIMARY KEY,
    reservation_request_id UUID NOT NULL,
    reservation_passenger_id UUID NOT NULL UNIQUE,
    train_run_id UUID NOT NULL,
    inventory_berth_id UUID NOT NULL,
    inventory_hold_id UUID NOT NULL UNIQUE,
    from_stop_index SMALLINT NOT NULL,
    to_stop_index SMALLINT NOT NULL,
    coach_class_code VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ALLOCATED_PENDING_BOOKING',
    preferred_berth_type VARCHAR(24),
    preference_result VARCHAR(24) NOT NULL,
    allocation_policy_version SMALLINT NOT NULL REFERENCES reservation_allocation_policies(version_number),
    allocation_score INTEGER NOT NULL,
    allocated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_allocations_id_run_berth_unique
        UNIQUE (reservation_allocation_id, train_run_id, inventory_berth_id),
    CONSTRAINT reservation_allocations_request_run_fk
        FOREIGN KEY (reservation_request_id, train_run_id)
        REFERENCES reservation_requests(reservation_request_id, train_run_id),
    CONSTRAINT reservation_allocations_passenger_request_fk
        FOREIGN KEY (reservation_passenger_id, reservation_request_id)
        REFERENCES reservation_passengers(reservation_passenger_id, reservation_request_id),
    CONSTRAINT reservation_allocations_run_berth_fk
        FOREIGN KEY (train_run_id, inventory_berth_id)
        REFERENCES inventory_berths(train_run_id, inventory_berth_id),
    CONSTRAINT reservation_allocations_hold_fk
        FOREIGN KEY (inventory_hold_id, train_run_id, inventory_berth_id, from_stop_index, to_stop_index)
        REFERENCES inventory_holds(
            inventory_hold_id, train_run_id, inventory_berth_id, from_stop_index, to_stop_index
        ),
    CONSTRAINT reservation_allocations_status_check
        CHECK (status IN ('ALLOCATED_PENDING_BOOKING', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT reservation_allocations_preference_check CHECK (
        (preferred_berth_type IS NULL AND preference_result = 'NO_PREFERENCE')
        OR (preferred_berth_type IS NOT NULL AND preference_result IN ('SATISFIED', 'ALTERNATIVE'))
    ),
    CONSTRAINT reservation_allocations_range_check
        CHECK (from_stop_index >= 0 AND to_stop_index > from_stop_index),
    CONSTRAINT reservation_allocations_preference_type_check CHECK (
        preferred_berth_type IS NULL OR preferred_berth_type IN
            ('LOWER_BERTH', 'MIDDLE_BERTH', 'UPPER_BERTH', 'SIDE_LOWER_BERTH', 'SIDE_UPPER_BERTH')
    ),
    CONSTRAINT reservation_allocations_score_check CHECK (allocation_score >= 0)
);

CREATE INDEX reservation_allocations_request_idx
    ON reservation_allocations(reservation_request_id, allocated_at);
CREATE INDEX reservation_allocations_berth_idx
    ON reservation_allocations(train_run_id, inventory_berth_id, status);
CREATE INDEX reservation_allocations_expiry_idx
    ON reservation_requests(expires_at, reservation_request_id)
    WHERE status = 'ALLOCATED_PENDING_BOOKING';

CREATE TABLE reservation_allocation_segments (
    reservation_allocation_id UUID NOT NULL,
    train_run_id UUID NOT NULL,
    inventory_berth_id UUID NOT NULL,
    segment_index SMALLINT NOT NULL,
    PRIMARY KEY (reservation_allocation_id, segment_index),
    CONSTRAINT reservation_allocation_segments_composite_unique
        UNIQUE (reservation_allocation_id, train_run_id, inventory_berth_id, segment_index),
    CONSTRAINT reservation_allocation_segments_allocation_fk
        FOREIGN KEY (reservation_allocation_id, train_run_id, inventory_berth_id)
        REFERENCES reservation_allocations(
            reservation_allocation_id, train_run_id, inventory_berth_id
        ),
    CONSTRAINT reservation_allocation_segments_run_fk
        FOREIGN KEY (train_run_id, segment_index)
        REFERENCES inventory_run_segments(train_run_id, segment_index),
    CONSTRAINT reservation_allocation_segments_index_check CHECK (segment_index >= 0)
);

CREATE INDEX reservation_allocation_segments_berth_idx
    ON reservation_allocation_segments(inventory_berth_id, segment_index);

ALTER TABLE inventory_segment_claims
    ADD COLUMN reservation_allocation_id UUID;

ALTER TABLE inventory_segment_claims
    DROP CONSTRAINT inventory_segment_claims_owner_check;

ALTER TABLE inventory_segment_claims
    ADD CONSTRAINT inventory_segment_claims_allocation_segment_fk
        FOREIGN KEY (reservation_allocation_id, train_run_id, inventory_berth_id, segment_index)
        REFERENCES reservation_allocation_segments(
            reservation_allocation_id, train_run_id, inventory_berth_id, segment_index
        ),
    ADD CONSTRAINT inventory_segment_claims_owner_check CHECK (
        num_nonnulls(inventory_hold_id, inventory_block_id, reservation_allocation_id) = 1
    );

CREATE INDEX inventory_segment_claims_allocation_idx
    ON inventory_segment_claims(reservation_allocation_id)
    WHERE reservation_allocation_id IS NOT NULL;

CREATE FUNCTION validate_reservation_allocation_segment_range() RETURNS TRIGGER AS $$
DECLARE
    allocation_from SMALLINT;
    allocation_to SMALLINT;
BEGIN
    SELECT from_stop_index, to_stop_index INTO allocation_from, allocation_to
    FROM reservation_allocations
    WHERE reservation_allocation_id = NEW.reservation_allocation_id
      AND train_run_id = NEW.train_run_id
      AND inventory_berth_id = NEW.inventory_berth_id;
    IF NEW.segment_index < allocation_from OR NEW.segment_index >= allocation_to THEN
        RAISE EXCEPTION 'Allocation segment is outside the passenger journey range';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservation_allocation_segment_range_guard
    BEFORE INSERT OR UPDATE ON reservation_allocation_segments
    FOR EACH ROW EXECUTE FUNCTION validate_reservation_allocation_segment_range();

CREATE FUNCTION guard_reservation_allocation_segment_mutation() RETURNS TRIGGER AS $$
DECLARE
    allocation_status VARCHAR(32);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Reservation allocation segments are immutable';
    END IF;
    SELECT status INTO allocation_status
    FROM reservation_allocations
    WHERE reservation_allocation_id = NEW.reservation_allocation_id;
    IF allocation_status <> 'ALLOCATED_PENDING_BOOKING' THEN
        RAISE EXCEPTION 'Segments can only be recorded for a provisional allocation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservation_allocation_segments_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON reservation_allocation_segments
    FOR EACH ROW EXECUTE FUNCTION guard_reservation_allocation_segment_mutation();

CREATE FUNCTION enforce_reservation_request_state_transition() RETURNS TRIGGER AS $$
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
    ) THEN
        RAISE EXCEPTION 'Invalid reservation request state transition: % -> %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservation_request_state_transition_guard
    BEFORE UPDATE OR DELETE ON reservation_requests
    FOR EACH ROW EXECUTE FUNCTION enforce_reservation_request_state_transition();

CREATE FUNCTION enforce_reservation_allocation_state_transition() RETURNS TRIGGER AS $$
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
        OLD.status = 'ALLOCATED_PENDING_BOOKING'
        AND NEW.status IN ('CONFIRMED', 'RELEASED', 'EXPIRED')
    ) THEN
        RAISE EXCEPTION 'Invalid reservation allocation state transition: % -> %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservation_allocation_state_transition_guard
    BEFORE UPDATE OR DELETE ON reservation_allocations
    FOR EACH ROW EXECUTE FUNCTION enforce_reservation_allocation_state_transition();

CREATE TABLE reservation_state_history (
    reservation_state_history_id UUID PRIMARY KEY,
    reservation_request_id UUID NOT NULL REFERENCES reservation_requests(reservation_request_id),
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64),
    actor_user_id UUID REFERENCES users(user_id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT reservation_state_history_status_check CHECK (
        new_status IN ('REQUESTED', 'ALLOCATED_PENDING_BOOKING', 'CONFIRMED',
                       'RELEASED', 'FAILED', 'EXPIRED')
    )
);

CREATE INDEX reservation_state_history_request_idx
    ON reservation_state_history(reservation_request_id, occurred_at, reservation_state_history_id);

CREATE FUNCTION prevent_reservation_state_history_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Reservation state history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservation_state_history_append_only
    BEFORE UPDATE OR DELETE ON reservation_state_history
    FOR EACH ROW EXECUTE FUNCTION prevent_reservation_state_history_mutation();

CREATE TABLE reservation_audit_log (
    reservation_audit_id UUID PRIMARY KEY,
    reservation_request_id UUID NOT NULL REFERENCES reservation_requests(reservation_request_id),
    actor_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(32) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT reservation_audit_event_type_check CHECK (
        event_type IN ('REQUEST_CREATED', 'ALLOCATION_CREATED', 'ALLOCATION_FAILED',
                       'ALLOCATION_CONFIRMED', 'ALLOCATION_RELEASED', 'ALLOCATION_EXPIRED')
    ),
    CONSTRAINT reservation_audit_details_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX reservation_audit_request_idx
    ON reservation_audit_log(reservation_request_id, occurred_at DESC);

CREATE FUNCTION prevent_reservation_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Reservation audit records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reservation_audit_append_only
    BEFORE UPDATE OR DELETE ON reservation_audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_reservation_audit_mutation();

CREATE TABLE reservation_policy_audit_log (
    reservation_policy_audit_id UUID PRIMARY KEY,
    version_number SMALLINT NOT NULL
        REFERENCES reservation_allocation_policies(version_number),
    actor_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(32) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT reservation_policy_audit_event_check CHECK (
        event_type IN ('POLICY_DRAFT_CREATED', 'POLICY_APPROVED', 'POLICY_PUBLISHED')
    ),
    CONSTRAINT reservation_policy_audit_details_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX reservation_policy_audit_version_idx
    ON reservation_policy_audit_log(version_number, occurred_at DESC);

CREATE TRIGGER reservation_policy_audit_append_only
    BEFORE UPDATE OR DELETE ON reservation_policy_audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_reservation_audit_mutation();

CREATE TABLE reservation_request_rate_limits (
    owner_user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    CONSTRAINT reservation_request_rate_count_check CHECK (request_count > 0)
);

ALTER TABLE inventory_audit_log
    DROP CONSTRAINT inventory_audit_event_type_check;

ALTER TABLE inventory_audit_log
    ADD CONSTRAINT inventory_audit_event_type_check CHECK (
        event_type IN ('RUN_INITIALIZED', 'HOLD_CREATED', 'HOLD_RELEASED', 'HOLD_EXPIRED',
                       'HOLD_CONSUMED', 'BERTH_BLOCKED', 'BERTH_UNBLOCKED')
    );

INSERT INTO permissions (permission_code, description) VALUES
    ('reservations:own:create', 'Create own provisional reservation allocation requests'),
    ('reservations:own:read', 'Read own reservation allocation requests'),
    ('reservations:own:allocate', 'Allocate berths from own active inventory holds'),
    ('reservations:own:release', 'Release own provisional reservation allocations'),
    ('reservations:admin:read', 'Read authorized reservation and allocation summaries'),
    ('reservations:admin:release', 'Release authorized provisional reservation allocations'),
    ('reservations:policy:read', 'Read reservation allocation policy versions'),
    ('reservations:policy:manage', 'Create reservation allocation policy drafts'),
    ('reservations:policy:approve', 'Approve reservation allocation policy versions'),
    ('reservations:policy:publish', 'Publish approved reservation allocation policy versions'),
    ('reservations:operations:summary', 'Read operational allocation counts without passenger details')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'PASSENGER', permission_code
FROM permissions
WHERE permission_code IN (
    'reservations:own:create', 'reservations:own:read',
    'reservations:own:allocate', 'reservations:own:release'
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', permission_code
FROM permissions
WHERE permission_code LIKE 'reservations:%'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('RAILWAY_ADMIN', 'reservations:admin:read'),
    ('RAILWAY_ADMIN', 'reservations:admin:release'),
    ('RAILWAY_ADMIN', 'reservations:policy:read'),
    ('RAILWAY_ADMIN', 'reservations:policy:manage'),
    ('RESERVATION_OFFICER', 'reservations:admin:read'),
    ('RESERVATION_OFFICER', 'reservations:admin:release'),
    ('RESERVATION_OFFICER', 'reservations:policy:read'),
    ('OPERATIONS_MANAGER', 'reservations:operations:summary')
ON CONFLICT DO NOTHING;
