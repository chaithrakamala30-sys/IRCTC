CREATE TABLE fare_passenger_categories (
    passenger_category_id UUID PRIMARY KEY,
    category_code VARCHAR(32) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    minimum_age SMALLINT,
    maximum_age SMALLINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fare_passenger_category_ages_check CHECK (
        (minimum_age IS NULL OR minimum_age BETWEEN 0 AND 120) AND
        (maximum_age IS NULL OR maximum_age BETWEEN 0 AND 120) AND
        (minimum_age IS NULL OR maximum_age IS NULL OR minimum_age <= maximum_age)
    )
);

INSERT INTO fare_passenger_categories (
    passenger_category_id, category_code, category_name
) VALUES (
    gen_random_uuid(), 'ADULT', 'Adult'
);

CREATE TABLE fare_rules (
    fare_rule_id UUID PRIMARY KEY,
    rule_name VARCHAR(160) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE fare_rule_versions (
    fare_rule_version_id UUID PRIMARY KEY,
    fare_rule_id UUID NOT NULL REFERENCES fare_rules(fare_rule_id),
    version_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    train_id UUID REFERENCES trains(train_id),
    category_id UUID REFERENCES train_categories(category_id),
    coach_class_id UUID REFERENCES coach_classes(coach_class_id),
    pricing_model VARCHAR(24) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    base_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    per_km_rate NUMERIC(12, 4) NOT NULL DEFAULT 0,
    priority SMALLINT NOT NULL DEFAULT 0,
    effective_from DATE NOT NULL,
    effective_to DATE,
    change_reason VARCHAR(500) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL REFERENCES users(user_id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMPTZ,
    approved_by UUID REFERENCES users(user_id),
    approved_at TIMESTAMPTZ,
    published_by UUID REFERENCES users(user_id),
    published_at TIMESTAMPTZ,
    CONSTRAINT fare_rule_version_number_unique UNIQUE (fare_rule_id, version_number),
    CONSTRAINT fare_rule_status_check CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT fare_rule_pricing_model_check CHECK (
        pricing_model IN ('DISTANCE_RATE', 'BASE_PLUS_DISTANCE', 'DISTANCE_SLABS')
    ),
    CONSTRAINT fare_rule_currency_check CHECK (currency = 'INR'),
    CONSTRAINT fare_rule_money_check CHECK (base_amount >= 0 AND per_km_rate >= 0),
    CONSTRAINT fare_rule_priority_check CHECK (priority BETWEEN 0 AND 999),
    CONSTRAINT fare_rule_effective_dates_check CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT fare_rule_lifecycle_check CHECK (
        (status NOT IN ('SUBMITTED', 'APPROVED', 'PUBLISHED') OR submitted_at IS NOT NULL) AND
        (status NOT IN ('APPROVED', 'PUBLISHED') OR (approved_at IS NOT NULL AND approved_by IS NOT NULL)) AND
        (status <> 'PUBLISHED' OR (published_at IS NOT NULL AND published_by IS NOT NULL))
    )
);

CREATE TABLE fare_rule_version_authors (
    fare_rule_version_id UUID NOT NULL REFERENCES fare_rule_versions(fare_rule_version_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id),
    PRIMARY KEY (fare_rule_version_id, user_id)
);

INSERT INTO fare_rule_version_authors (fare_rule_version_id, user_id)
SELECT fare_rule_version_id, created_by FROM fare_rule_versions;

CREATE INDEX fare_rule_version_selection_idx ON fare_rule_versions (
    status, effective_from, effective_to, train_id, category_id, coach_class_id, priority
);

CREATE FUNCTION guard_fare_rule_version_update() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status <> 'DRAFT' AND (
        NEW.fare_rule_id IS DISTINCT FROM OLD.fare_rule_id OR
        NEW.version_number IS DISTINCT FROM OLD.version_number OR
        NEW.train_id IS DISTINCT FROM OLD.train_id OR
        NEW.category_id IS DISTINCT FROM OLD.category_id OR
        NEW.coach_class_id IS DISTINCT FROM OLD.coach_class_id OR
        NEW.pricing_model IS DISTINCT FROM OLD.pricing_model OR
        NEW.currency IS DISTINCT FROM OLD.currency OR
        NEW.base_amount IS DISTINCT FROM OLD.base_amount OR
        NEW.per_km_rate IS DISTINCT FROM OLD.per_km_rate OR
        NEW.priority IS DISTINCT FROM OLD.priority OR
        NEW.effective_from IS DISTINCT FROM OLD.effective_from OR
        NEW.effective_to IS DISTINCT FROM OLD.effective_to OR
        NEW.change_reason IS DISTINCT FROM OLD.change_reason OR
        NEW.created_by IS DISTINCT FROM OLD.created_by OR
        NEW.created_at IS DISTINCT FROM OLD.created_at
    ) THEN
        RAISE EXCEPTION 'Submitted and approved fare rule versions are immutable';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
        (OLD.status = 'DRAFT' AND NEW.status = 'SUBMITTED') OR
        (OLD.status = 'SUBMITTED' AND NEW.status = 'APPROVED') OR
        (OLD.status = 'APPROVED' AND NEW.status = 'PUBLISHED') OR
        (OLD.status = 'PUBLISHED' AND NEW.status = 'RETIRED')
    ) THEN
        RAISE EXCEPTION 'Invalid fare rule version lifecycle transition';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fare_rule_version_update_guard
    BEFORE UPDATE ON fare_rule_versions
    FOR EACH ROW EXECUTE FUNCTION guard_fare_rule_version_update();

CREATE FUNCTION guard_fare_rule_child_mutation() RETURNS TRIGGER AS $$
DECLARE
    version_id UUID;
    version_status VARCHAR(16);
BEGIN
    IF TG_OP = 'DELETE' THEN
        version_id := OLD.fare_rule_version_id;
    ELSE
        version_id := NEW.fare_rule_version_id;
    END IF;
    SELECT status INTO version_status
    FROM fare_rule_versions WHERE fare_rule_version_id = version_id;
    IF version_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Submitted and approved fare rule content is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE fare_rule_distance_slabs (
    fare_rule_version_id UUID NOT NULL REFERENCES fare_rule_versions(fare_rule_version_id) ON DELETE CASCADE,
    minimum_distance_km NUMERIC(9, 2) NOT NULL,
    maximum_distance_km NUMERIC(9, 2),
    base_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    per_km_rate NUMERIC(12, 4) NOT NULL DEFAULT 0,
    PRIMARY KEY (fare_rule_version_id, minimum_distance_km),
    CONSTRAINT fare_slab_distance_check CHECK (
        minimum_distance_km >= 0 AND (maximum_distance_km IS NULL OR maximum_distance_km > minimum_distance_km)
    ),
    CONSTRAINT fare_slab_money_check CHECK (base_amount >= 0 AND per_km_rate >= 0)
);

CREATE TABLE fare_rule_components (
    fare_rule_component_id UUID PRIMARY KEY,
    fare_rule_version_id UUID NOT NULL REFERENCES fare_rule_versions(fare_rule_version_id) ON DELETE CASCADE,
    passenger_category_id UUID REFERENCES fare_passenger_categories(passenger_category_id),
    component_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    component_type VARCHAR(16) NOT NULL,
    calculation_method VARCHAR(24) NOT NULL,
    amount NUMERIC(14, 4) NOT NULL,
    maximum_amount NUMERIC(14, 2),
    minimum_distance_km NUMERIC(9, 2) NOT NULL DEFAULT 0,
    calculation_order SMALLINT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fare_component_code_unique UNIQUE (fare_rule_version_id, component_code),
    CONSTRAINT fare_component_type_check CHECK (component_type IN ('CHARGE', 'TAX', 'DISCOUNT')),
    CONSTRAINT fare_component_method_check CHECK (
        calculation_method IN ('FIXED', 'PER_KM', 'PERCENT_OF_BASE', 'PERCENT_OF_SUBTOTAL')
    ),
    CONSTRAINT fare_component_amount_check CHECK (
        amount >= 0 AND (maximum_amount IS NULL OR maximum_amount >= 0) AND minimum_distance_km >= 0
    ),
    CONSTRAINT fare_component_order_check CHECK (calculation_order BETWEEN 0 AND 999)
);

CREATE TABLE fare_rule_passenger_adjustments (
    fare_rule_version_id UUID NOT NULL REFERENCES fare_rule_versions(fare_rule_version_id) ON DELETE CASCADE,
    passenger_category_id UUID NOT NULL REFERENCES fare_passenger_categories(passenger_category_id),
    adjustment_type VARCHAR(24) NOT NULL,
    adjustment_value NUMERIC(14, 4) NOT NULL,
    maximum_amount NUMERIC(14, 2),
    PRIMARY KEY (fare_rule_version_id, passenger_category_id),
    CONSTRAINT fare_adjustment_type_check CHECK (
        adjustment_type IN ('DISCOUNT_PERCENT', 'DISCOUNT_FIXED', 'CHARGE_PERCENT', 'CHARGE_FIXED')
    ),
    CONSTRAINT fare_adjustment_amount_check CHECK (
        adjustment_value >= 0 AND (maximum_amount IS NULL OR maximum_amount >= 0) AND
        (adjustment_type NOT LIKE '%PERCENT' OR adjustment_value <= 100)
    )
);

CREATE TRIGGER fare_rule_slab_mutation_guard
    BEFORE INSERT OR UPDATE OR DELETE ON fare_rule_distance_slabs
    FOR EACH ROW EXECUTE FUNCTION guard_fare_rule_child_mutation();
CREATE TRIGGER fare_rule_component_mutation_guard
    BEFORE INSERT OR UPDATE OR DELETE ON fare_rule_components
    FOR EACH ROW EXECUTE FUNCTION guard_fare_rule_child_mutation();
CREATE TRIGGER fare_rule_adjustment_mutation_guard
    BEFORE INSERT OR UPDATE OR DELETE ON fare_rule_passenger_adjustments
    FOR EACH ROW EXECUTE FUNCTION guard_fare_rule_child_mutation();
CREATE TRIGGER fare_rule_authors_mutation_guard
    BEFORE INSERT OR UPDATE OR DELETE ON fare_rule_version_authors
    FOR EACH ROW EXECUTE FUNCTION guard_fare_rule_child_mutation();

CREATE TABLE fare_rule_audit_events (
    fare_rule_audit_event_id UUID PRIMARY KEY,
    fare_rule_version_id UUID NOT NULL REFERENCES fare_rule_versions(fare_rule_version_id),
    actor_user_id UUID NOT NULL REFERENCES users(user_id),
    event_type VARCHAR(24) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fare_rule_audit_event_type_check CHECK (
        event_type IN ('CREATED', 'UPDATED', 'SUBMITTED', 'APPROVED', 'PUBLISHED', 'RETIRED')
    )
);

CREATE INDEX fare_rule_audit_recent_idx ON fare_rule_audit_events (occurred_at DESC);
CREATE INDEX fare_rule_audit_version_idx ON fare_rule_audit_events (fare_rule_version_id, occurred_at DESC);

CREATE FUNCTION prevent_fare_rule_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Fare rule audit events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fare_rule_audit_append_only
    BEFORE UPDATE OR DELETE ON fare_rule_audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_fare_rule_audit_mutation();

CREATE TABLE fare_quotes (
    fare_quote_id UUID PRIMARY KEY,
    owner_user_id UUID REFERENCES users(user_id),
    train_id UUID NOT NULL REFERENCES trains(train_id),
    origin_station_id UUID NOT NULL REFERENCES stations(station_id),
    destination_station_id UUID NOT NULL REFERENCES stations(station_id),
    journey_date DATE NOT NULL,
    coach_class_code VARCHAR(32) NOT NULL,
    fare_rule_version_id UUID NOT NULL REFERENCES fare_rule_versions(fare_rule_version_id),
    distance_km NUMERIC(9, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    total_amount NUMERIC(14, 2) NOT NULL,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fare_quote_currency_check CHECK (currency = 'INR'),
    CONSTRAINT fare_quote_amount_check CHECK (distance_km > 0 AND total_amount >= 0),
    CONSTRAINT fare_quote_stations_check CHECK (origin_station_id <> destination_station_id),
    CONSTRAINT fare_quote_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX fare_quote_expiry_idx ON fare_quotes (expires_at);
CREATE INDEX fare_quote_owner_idx ON fare_quotes (owner_user_id, created_at DESC)
    WHERE owner_user_id IS NOT NULL;

CREATE FUNCTION prevent_fare_quote_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Fare quote snapshots are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER fare_quote_immutable
    BEFORE UPDATE OR DELETE ON fare_quotes
    FOR EACH ROW EXECUTE FUNCTION prevent_fare_quote_mutation();

INSERT INTO permissions (permission_code, description) VALUES
    ('fares:rules:read', 'Read fare rules, passenger categories, and quote records'),
    ('fares:rules:manage', 'Create and edit draft fare-rule versions'),
    ('fares:rules:approve', 'Approve submitted fare-rule versions authored by another user'),
    ('fares:rules:publish', 'Publish approved fare-rule versions authored by another user');

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', permission_code
FROM permissions
WHERE permission_code LIKE 'fares:rules:%';

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('RAILWAY_ADMIN', 'fares:rules:read'),
    ('RAILWAY_ADMIN', 'fares:rules:manage'),
    ('FINANCE_ADMIN', 'fares:rules:read'),
    ('FINANCE_ADMIN', 'fares:rules:manage'),
    ('FINANCE_ADMIN', 'fares:rules:approve'),
    ('FINANCE_ADMIN', 'fares:rules:publish');
