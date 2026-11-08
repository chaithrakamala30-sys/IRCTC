CREATE TABLE inventory_train_runs (
    train_run_id UUID PRIMARY KEY,
    train_id UUID NOT NULL REFERENCES trains(train_id),
    service_date DATE NOT NULL,
    schedule_id UUID NOT NULL REFERENCES train_schedules(schedule_id),
    route_version_id UUID NOT NULL REFERENCES train_route_versions(route_version_id),
    composition_version_id UUID NOT NULL REFERENCES train_composition_versions(composition_version_id),
    status VARCHAR(16) NOT NULL DEFAULT 'INITIALIZING',
    route_snapshot JSONB NOT NULL,
    composition_snapshot JSONB NOT NULL,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_train_runs_train_date_unique UNIQUE (train_id, service_date),
    CONSTRAINT inventory_train_runs_status_check CHECK (status IN ('INITIALIZING', 'READY', 'CANCELLED')),
    CONSTRAINT inventory_train_runs_route_snapshot_check CHECK (jsonb_typeof(route_snapshot) = 'array'),
    CONSTRAINT inventory_train_runs_composition_snapshot_check CHECK (jsonb_typeof(composition_snapshot) = 'array')
);

CREATE INDEX inventory_train_runs_date_idx ON inventory_train_runs(service_date, status, train_id);

CREATE FUNCTION prevent_inventory_run_snapshot_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.train_run_id = OLD.train_run_id
       AND NEW.train_id = OLD.train_id
       AND NEW.service_date = OLD.service_date
       AND NEW.schedule_id = OLD.schedule_id
       AND NEW.route_version_id = OLD.route_version_id
       AND NEW.composition_version_id = OLD.composition_version_id
       AND NEW.route_snapshot = OLD.route_snapshot
       AND NEW.composition_snapshot = OLD.composition_snapshot
       AND NEW.created_by IS NOT DISTINCT FROM OLD.created_by
       AND NEW.created_at = OLD.created_at
       AND NEW.initialized_at = OLD.initialized_at
       AND (
           (OLD.status = 'INITIALIZING' AND NEW.status = 'READY')
           OR (OLD.status = 'READY' AND NEW.status = 'CANCELLED')
       ) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Train-run inventory snapshots are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_train_run_snapshot_immutable
    BEFORE UPDATE OR DELETE ON inventory_train_runs
    FOR EACH ROW EXECUTE FUNCTION prevent_inventory_run_snapshot_mutation();

CREATE FUNCTION guard_inventory_snapshot_detail_mutation() RETURNS TRIGGER AS $$
DECLARE
    run_status VARCHAR(16);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT status INTO run_status
        FROM inventory_train_runs
        WHERE train_run_id = NEW.train_run_id;
        IF run_status = 'INITIALIZING' THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'Train-run snapshot details are immutable after initialization';
END;
$$ LANGUAGE plpgsql;

CREATE TABLE inventory_run_stops (
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    stop_index SMALLINT NOT NULL,
    route_stop_id UUID NOT NULL REFERENCES train_route_stops(route_stop_id),
    station_id UUID NOT NULL REFERENCES stations(station_id),
    station_code VARCHAR(20) NOT NULL,
    station_name VARCHAR(160) NOT NULL,
    stop_sequence INTEGER NOT NULL,
    arrival_time TIME,
    departure_time TIME,
    arrival_day_offset SMALLINT NOT NULL,
    departure_day_offset SMALLINT NOT NULL,
    distance_from_source_km NUMERIC(9, 2) NOT NULL,
    PRIMARY KEY (train_run_id, stop_index),
    CONSTRAINT inventory_run_stops_route_id_unique UNIQUE (train_run_id, route_stop_id),
    CONSTRAINT inventory_run_stops_station_unique UNIQUE (train_run_id, station_id),
    CONSTRAINT inventory_run_stops_index_check CHECK (stop_index >= 0),
    CONSTRAINT inventory_run_stops_distance_check CHECK (distance_from_source_km >= 0)
);

CREATE TABLE inventory_run_segments (
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    segment_index SMALLINT NOT NULL,
    from_stop_index SMALLINT NOT NULL,
    to_stop_index SMALLINT NOT NULL,
    from_station_id UUID NOT NULL REFERENCES stations(station_id),
    to_station_id UUID NOT NULL REFERENCES stations(station_id),
    distance_km NUMERIC(9, 2) NOT NULL,
    PRIMARY KEY (train_run_id, segment_index),
    CONSTRAINT inventory_run_segments_range_check CHECK (
        segment_index = from_stop_index AND to_stop_index = from_stop_index + 1
    ),
    CONSTRAINT inventory_run_segments_distance_check CHECK (distance_km > 0),
    CONSTRAINT inventory_run_segments_station_check CHECK (from_station_id <> to_station_id),
    CONSTRAINT inventory_run_segments_from_stop_fk
        FOREIGN KEY (train_run_id, from_stop_index)
        REFERENCES inventory_run_stops(train_run_id, stop_index),
    CONSTRAINT inventory_run_segments_to_stop_fk
        FOREIGN KEY (train_run_id, to_stop_index)
        REFERENCES inventory_run_stops(train_run_id, stop_index)
);

CREATE TABLE inventory_coaches (
    inventory_coach_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    source_coach_id UUID NOT NULL REFERENCES coaches(coach_id),
    coach_number VARCHAR(20) NOT NULL,
    position INTEGER NOT NULL,
    configuration_id UUID NOT NULL REFERENCES coach_configurations(configuration_id),
    coach_class_id UUID NOT NULL REFERENCES coach_classes(coach_class_id),
    coach_class_code VARCHAR(32) NOT NULL,
    coach_class_name VARCHAR(100) NOT NULL,
    coach_type_name VARCHAR(100) NOT NULL,
    configuration_snapshot JSONB NOT NULL,
    CONSTRAINT inventory_coaches_run_number_unique UNIQUE (train_run_id, coach_number),
    CONSTRAINT inventory_coaches_run_position_unique UNIQUE (train_run_id, position),
    CONSTRAINT inventory_coaches_position_check CHECK (position > 0),
    CONSTRAINT inventory_coaches_snapshot_check CHECK (jsonb_typeof(configuration_snapshot) = 'object')
);

CREATE INDEX inventory_coaches_class_idx ON inventory_coaches(train_run_id, coach_class_code, position);
ALTER TABLE inventory_coaches
    ADD CONSTRAINT inventory_coaches_run_berth_fk UNIQUE (train_run_id, inventory_coach_id);

CREATE TABLE inventory_berths (
    inventory_berth_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    inventory_coach_id UUID NOT NULL REFERENCES inventory_coaches(inventory_coach_id),
    source_accommodation_id UUID NOT NULL REFERENCES physical_accommodations(accommodation_id),
    berth_number VARCHAR(20) NOT NULL,
    berth_type VARCHAR(24) NOT NULL,
    display_order INTEGER NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::JSONB,
    base_status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT inventory_berths_coach_number_unique UNIQUE (inventory_coach_id, berth_number),
    CONSTRAINT inventory_berths_coach_order_unique UNIQUE (inventory_coach_id, display_order),
    CONSTRAINT inventory_berths_run_id_unique UNIQUE (train_run_id, inventory_berth_id),
    CONSTRAINT inventory_berths_run_coach_fk
        FOREIGN KEY (train_run_id, inventory_coach_id)
        REFERENCES inventory_coaches(train_run_id, inventory_coach_id),
    CONSTRAINT inventory_berths_status_check CHECK (base_status IN ('AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT inventory_berths_attributes_check CHECK (jsonb_typeof(attributes) = 'object')
);

CREATE INDEX inventory_berths_coach_order_idx ON inventory_berths(inventory_coach_id, display_order);

CREATE TRIGGER inventory_run_stops_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON inventory_run_stops
    FOR EACH ROW EXECUTE FUNCTION guard_inventory_snapshot_detail_mutation();

CREATE TRIGGER inventory_run_segments_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON inventory_run_segments
    FOR EACH ROW EXECUTE FUNCTION guard_inventory_snapshot_detail_mutation();

CREATE TRIGGER inventory_coaches_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON inventory_coaches
    FOR EACH ROW EXECUTE FUNCTION guard_inventory_snapshot_detail_mutation();

CREATE TRIGGER inventory_berths_immutable
    BEFORE INSERT OR UPDATE OR DELETE ON inventory_berths
    FOR EACH ROW EXECUTE FUNCTION guard_inventory_snapshot_detail_mutation();

CREATE TABLE inventory_holds (
    inventory_hold_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    inventory_berth_id UUID NOT NULL REFERENCES inventory_berths(inventory_berth_id),
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    from_stop_index SMALLINT NOT NULL,
    to_stop_index SMALLINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_holds_idempotency_unique UNIQUE (owner_user_id, idempotency_key),
    CONSTRAINT inventory_holds_range_unique
        UNIQUE (inventory_hold_id, train_run_id, inventory_berth_id, from_stop_index, to_stop_index),
    CONSTRAINT inventory_holds_run_berth_unique
        UNIQUE (inventory_hold_id, train_run_id, inventory_berth_id),
    CONSTRAINT inventory_holds_run_berth_fk
        FOREIGN KEY (train_run_id, inventory_berth_id)
        REFERENCES inventory_berths(train_run_id, inventory_berth_id),
    CONSTRAINT inventory_holds_status_check CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED')),
    CONSTRAINT inventory_holds_range_check CHECK (from_stop_index >= 0 AND to_stop_index > from_stop_index),
    CONSTRAINT inventory_holds_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX inventory_holds_expiry_idx ON inventory_holds(expires_at, inventory_hold_id)
    WHERE status = 'ACTIVE';
CREATE INDEX inventory_holds_owner_idx ON inventory_holds(owner_user_id, created_at DESC);
CREATE INDEX inventory_holds_run_idx ON inventory_holds(train_run_id, status, expires_at);

CREATE TABLE inventory_blocks (
    inventory_block_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    inventory_berth_id UUID NOT NULL REFERENCES inventory_berths(inventory_berth_id),
    from_stop_index SMALLINT NOT NULL,
    to_stop_index SMALLINT NOT NULL,
    block_type VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_by UUID REFERENCES users(user_id),
    released_at TIMESTAMPTZ,
    CONSTRAINT inventory_blocks_status_check CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT inventory_blocks_range_unique
        UNIQUE (inventory_block_id, train_run_id, inventory_berth_id, from_stop_index, to_stop_index),
    CONSTRAINT inventory_blocks_run_berth_unique
        UNIQUE (inventory_block_id, train_run_id, inventory_berth_id),
    CONSTRAINT inventory_blocks_run_berth_fk
        FOREIGN KEY (train_run_id, inventory_berth_id)
        REFERENCES inventory_berths(train_run_id, inventory_berth_id),
    CONSTRAINT inventory_blocks_type_check CHECK (block_type IN ('BLOCKED', 'MAINTENANCE', 'UNAVAILABLE')),
    CONSTRAINT inventory_blocks_range_check CHECK (from_stop_index >= 0 AND to_stop_index > from_stop_index)
);

CREATE INDEX inventory_blocks_run_status_idx ON inventory_blocks(train_run_id, status, inventory_berth_id);

CREATE TABLE inventory_hold_segments (
    inventory_hold_id UUID NOT NULL REFERENCES inventory_holds(inventory_hold_id),
    train_run_id UUID NOT NULL,
    inventory_berth_id UUID NOT NULL REFERENCES inventory_berths(inventory_berth_id),
    segment_index SMALLINT NOT NULL,
    PRIMARY KEY (inventory_hold_id, segment_index),
    CONSTRAINT inventory_hold_segments_range_unique
        UNIQUE (inventory_hold_id, train_run_id, inventory_berth_id, segment_index),
    CONSTRAINT inventory_hold_segments_hold_fk
        FOREIGN KEY (inventory_hold_id, train_run_id, inventory_berth_id)
        REFERENCES inventory_holds(inventory_hold_id, train_run_id, inventory_berth_id),
    CONSTRAINT inventory_hold_segments_run_fk
        FOREIGN KEY (train_run_id, segment_index)
        REFERENCES inventory_run_segments(train_run_id, segment_index),
    CONSTRAINT inventory_hold_segments_index_check CHECK (segment_index >= 0)
);

CREATE INDEX inventory_hold_segments_berth_idx
    ON inventory_hold_segments(inventory_berth_id, segment_index, inventory_hold_id);

CREATE TABLE inventory_block_segments (
    inventory_block_id UUID NOT NULL REFERENCES inventory_blocks(inventory_block_id),
    train_run_id UUID NOT NULL,
    inventory_berth_id UUID NOT NULL REFERENCES inventory_berths(inventory_berth_id),
    segment_index SMALLINT NOT NULL,
    PRIMARY KEY (inventory_block_id, segment_index),
    CONSTRAINT inventory_block_segments_range_unique
        UNIQUE (inventory_block_id, train_run_id, inventory_berth_id, segment_index),
    CONSTRAINT inventory_block_segments_block_fk
        FOREIGN KEY (inventory_block_id, train_run_id, inventory_berth_id)
        REFERENCES inventory_blocks(inventory_block_id, train_run_id, inventory_berth_id),
    CONSTRAINT inventory_block_segments_run_fk
        FOREIGN KEY (train_run_id, segment_index)
        REFERENCES inventory_run_segments(train_run_id, segment_index),
    CONSTRAINT inventory_block_segments_index_check CHECK (segment_index >= 0)
);

CREATE TABLE inventory_segment_claims (
    train_run_id UUID NOT NULL,
    inventory_berth_id UUID NOT NULL REFERENCES inventory_berths(inventory_berth_id),
    segment_index SMALLINT NOT NULL,
    inventory_hold_id UUID REFERENCES inventory_holds(inventory_hold_id),
    inventory_block_id UUID REFERENCES inventory_blocks(inventory_block_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (inventory_berth_id, segment_index),
    CONSTRAINT inventory_segment_claims_run_fk
        FOREIGN KEY (train_run_id, segment_index)
        REFERENCES inventory_run_segments(train_run_id, segment_index),
    CONSTRAINT inventory_segment_claims_run_berth_fk
        FOREIGN KEY (train_run_id, inventory_berth_id)
        REFERENCES inventory_berths(train_run_id, inventory_berth_id),
    CONSTRAINT inventory_segment_claims_hold_segment_fk
        FOREIGN KEY (inventory_hold_id, train_run_id, inventory_berth_id, segment_index)
        REFERENCES inventory_hold_segments(inventory_hold_id, train_run_id, inventory_berth_id, segment_index),
    CONSTRAINT inventory_segment_claims_block_segment_fk
        FOREIGN KEY (inventory_block_id, train_run_id, inventory_berth_id, segment_index)
        REFERENCES inventory_block_segments(inventory_block_id, train_run_id, inventory_berth_id, segment_index),
    CONSTRAINT inventory_segment_claims_index_check CHECK (segment_index >= 0),
    CONSTRAINT inventory_segment_claims_owner_check CHECK (
        (inventory_hold_id IS NOT NULL AND inventory_block_id IS NULL) OR
        (inventory_hold_id IS NULL AND inventory_block_id IS NOT NULL)
    )
);

CREATE INDEX inventory_segment_claims_hold_idx ON inventory_segment_claims(inventory_hold_id)
    WHERE inventory_hold_id IS NOT NULL;
CREATE INDEX inventory_segment_claims_block_idx ON inventory_segment_claims(inventory_block_id)
    WHERE inventory_block_id IS NOT NULL;

CREATE FUNCTION validate_inventory_hold_segment_range() RETURNS TRIGGER AS $$
DECLARE
    requested_from SMALLINT;
    requested_to SMALLINT;
BEGIN
    SELECT from_stop_index, to_stop_index INTO requested_from, requested_to
    FROM inventory_holds
    WHERE inventory_hold_id = NEW.inventory_hold_id
      AND train_run_id = NEW.train_run_id
      AND inventory_berth_id = NEW.inventory_berth_id;
    IF NEW.segment_index < requested_from OR NEW.segment_index >= requested_to THEN
        RAISE EXCEPTION 'Hold segment is outside the requested journey range';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_hold_segment_range_guard
    BEFORE INSERT OR UPDATE ON inventory_hold_segments
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_hold_segment_range();

CREATE FUNCTION validate_inventory_block_segment_range() RETURNS TRIGGER AS $$
DECLARE
    requested_from SMALLINT;
    requested_to SMALLINT;
BEGIN
    SELECT from_stop_index, to_stop_index INTO requested_from, requested_to
    FROM inventory_blocks
    WHERE inventory_block_id = NEW.inventory_block_id
      AND train_run_id = NEW.train_run_id
      AND inventory_berth_id = NEW.inventory_berth_id;
    IF NEW.segment_index < requested_from OR NEW.segment_index >= requested_to THEN
        RAISE EXCEPTION 'Block segment is outside the blocked journey range';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_block_segment_range_guard
    BEFORE INSERT OR UPDATE ON inventory_block_segments
    FOR EACH ROW EXECUTE FUNCTION validate_inventory_block_segment_range();

CREATE TABLE inventory_audit_log (
    inventory_audit_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    inventory_berth_id UUID REFERENCES inventory_berths(inventory_berth_id),
    actor_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(32) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT inventory_audit_event_type_check CHECK (
        event_type IN ('RUN_INITIALIZED', 'HOLD_CREATED', 'HOLD_RELEASED', 'HOLD_EXPIRED',
                       'BERTH_BLOCKED', 'BERTH_UNBLOCKED')
    ),
    CONSTRAINT inventory_audit_details_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX inventory_audit_run_idx ON inventory_audit_log(train_run_id, occurred_at DESC);
CREATE INDEX inventory_audit_actor_idx ON inventory_audit_log(actor_user_id, occurred_at DESC);

CREATE FUNCTION prevent_inventory_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Inventory audit records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_audit_append_only
    BEFORE UPDATE OR DELETE ON inventory_audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_inventory_audit_mutation();

CREATE TABLE inventory_configuration (
    configuration_key VARCHAR(64) PRIMARY KEY,
    configuration_value INTEGER NOT NULL,
    updated_by UUID REFERENCES users(user_id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_configuration_positive_check CHECK (configuration_value > 0)
);

INSERT INTO inventory_configuration(configuration_key, configuration_value) VALUES
    ('MAX_ACTIVE_HOLDS_PER_PASSENGER', 4);

CREATE TABLE inventory_hold_rate_limits (
    owner_user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    CONSTRAINT inventory_hold_rate_count_check CHECK (request_count > 0)
);

INSERT INTO roles (role_code, display_name) VALUES
    ('OPERATIONS_MANAGER', 'Operations Manager')
ON CONFLICT DO NOTHING;

INSERT INTO permissions (permission_code, description) VALUES
    ('inventory:availability:read', 'Read train-run seat inventory availability'),
    ('inventory:holds:manage', 'Create and release passenger-owned seat holds'),
    ('inventory:runs:initialize', 'Initialize dated train-run inventory snapshots'),
    ('inventory:admin:read', 'Read authorized inventory summaries and audit'),
    ('inventory:blocks:manage', 'Block or release inventory berths for operations')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', permission_code
FROM permissions
WHERE permission_code LIKE 'inventory:%'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('RAILWAY_ADMIN', 'inventory:availability:read'),
    ('RAILWAY_ADMIN', 'inventory:runs:initialize'),
    ('RAILWAY_ADMIN', 'inventory:admin:read'),
    ('RAILWAY_ADMIN', 'inventory:blocks:manage'),
    ('OPERATIONS_MANAGER', 'inventory:availability:read'),
    ('OPERATIONS_MANAGER', 'inventory:admin:read'),
    ('OPERATIONS_MANAGER', 'inventory:blocks:manage'),
    ('PASSENGER', 'inventory:availability:read'),
    ('PASSENGER', 'inventory:holds:manage'),
    ('STATION_MANAGER', 'inventory:availability:read'),
    ('STATION_MANAGER', 'inventory:admin:read'),
    ('STATION_MANAGER', 'inventory:blocks:manage')
ON CONFLICT DO NOTHING;
