-- ============================================================================
-- RailConnect Phase 12 Database Migration: V16__create_advanced_reservations_rac_waitlist_and_charting.sql
-- Description: Configurable Quota Management, RAC-style queues, Waitlists,
--              Cancellation-driven atomic promotion, Departure chart preparation,
--              and operational manifest corrections.
-- ============================================================================

-- 1. Quota Definitions and Policy Versioning
CREATE TABLE reservation_quotas (
    quota_id UUID PRIMARY KEY,
    quota_code VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    description TEXT,
    eligibility_criteria_json TEXT, -- JSON rules specifying age, gender, concession certificates, etc.
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_quotas_code_check CHECK (
        quota_code IN (
            'GENERAL', 'TATKAL_STYLE', 'PREMIUM', 'LADIES', 'SENIOR_CITIZEN',
            'PERSONS_WITH_DISABILITIES', 'DEFENCE', 'STAFF', 'SPECIAL', 'CUSTOM'
        )
    )
);

CREATE TABLE quota_policy_versions (
    policy_version_id UUID PRIMARY KEY,
    version_number INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    notes TEXT,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quota_policy_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT quota_policy_effective_dates CHECK (effective_until IS NULL OR effective_until > effective_from)
);

CREATE UNIQUE INDEX quota_policy_one_published_idx ON quota_policy_versions(status) WHERE status = 'PUBLISHED';

-- 2. Quota Inventory Allocations per Train Run and Class
CREATE TABLE quota_inventory_allocations (
    allocation_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL REFERENCES reservation_quotas(quota_code),
    allocated_berths INT NOT NULL DEFAULT 0,
    rac_capacity INT NOT NULL DEFAULT 0,
    waitlist_capacity INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quota_allocations_non_negative CHECK (
        allocated_berths >= 0 AND rac_capacity >= 0 AND waitlist_capacity >= 0
    ),
    CONSTRAINT quota_allocations_unique_run_class_quota UNIQUE (train_run_id, coach_class_code, quota_code)
);

CREATE INDEX quota_allocations_run_class_idx ON quota_inventory_allocations(train_run_id, coach_class_code);

-- 3. Reservation Queues (RAC and Waitlist)
CREATE TABLE reservation_queues (
    queue_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL REFERENCES reservation_quotas(quota_code),
    queue_type VARCHAR(16) NOT NULL, -- 'RAC' or 'WAITLIST'
    current_highest_sequence INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_queue_type_check CHECK (queue_type IN ('RAC', 'WAITLIST')),
    CONSTRAINT reservation_queue_unique UNIQUE (train_run_id, coach_class_code, quota_code, queue_type)
);

CREATE INDEX reservation_queues_run_idx ON reservation_queues(train_run_id, coach_class_code, queue_type);

-- 4. Waitlist and RAC Entries
CREATE TABLE waitlist_entries (
    waitlist_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    booking_passenger_id UUID NOT NULL UNIQUE REFERENCES booking_passengers(booking_passenger_id),
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    from_station_id UUID NOT NULL REFERENCES stations(station_id),
    to_station_id UUID NOT NULL REFERENCES stations(station_id),
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL REFERENCES reservation_quotas(quota_code),
    waitlist_category VARCHAR(16) NOT NULL DEFAULT 'GNWL', -- GNWL, RLWL, PQWL, etc.
    initial_position INT NOT NULL,
    current_position INT NOT NULL,
    queue_sequence INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'WAITLISTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT waitlist_status_check CHECK (status IN ('WAITLISTED', 'PROMOTED_RAC', 'PROMOTED_CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT waitlist_position_positive CHECK (initial_position > 0 AND current_position >= 0 AND queue_sequence > 0)
);

CREATE INDEX waitlist_entries_queue_priority_idx ON waitlist_entries(train_run_id, coach_class_code, quota_code, status, queue_sequence);
CREATE INDEX waitlist_entries_booking_idx ON waitlist_entries(booking_id);

CREATE TABLE rac_entries (
    rac_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    booking_passenger_id UUID NOT NULL UNIQUE REFERENCES booking_passengers(booking_passenger_id),
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    from_station_id UUID NOT NULL REFERENCES stations(station_id),
    to_station_id UUID NOT NULL REFERENCES stations(station_id),
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL REFERENCES reservation_quotas(quota_code),
    initial_position INT NOT NULL,
    current_position INT NOT NULL,
    queue_sequence INT NOT NULL,
    assigned_berth_id UUID REFERENCES inventory_berths(inventory_berth_id),
    status VARCHAR(24) NOT NULL DEFAULT 'RAC',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rac_status_check CHECK (status IN ('RAC', 'PROMOTED_CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT rac_position_positive CHECK (initial_position > 0 AND current_position >= 0 AND queue_sequence > 0)
);

CREATE INDEX rac_entries_queue_priority_idx ON rac_entries(train_run_id, coach_class_code, quota_code, status, queue_sequence);
CREATE INDEX rac_entries_booking_idx ON rac_entries(booking_id);

-- 5. Reservation Promotions & History
CREATE TABLE reservation_promotions (
    promotion_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id),
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    promoted_from_position INT,
    promoted_to_position INT,
    freed_by_cancellation_id UUID,
    allocated_berth_id UUID REFERENCES inventory_berths(inventory_berth_id),
    promoted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_promotion_status_check CHECK (
        (from_status = 'WAITLISTED' AND to_status IN ('RAC', 'CONFIRMED')) OR
        (from_status = 'RAC' AND to_status = 'CONFIRMED')
    )
);

CREATE INDEX reservation_promotions_run_idx ON reservation_promotions(train_run_id, promoted_at DESC);
CREATE INDEX reservation_promotions_passenger_idx ON reservation_promotions(booking_passenger_id);

CREATE TABLE reservation_state_history (
    history_id UUID PRIMARY KEY,
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id),
    previous_state VARCHAR(24) NOT NULL,
    new_state VARCHAR(24) NOT NULL,
    reason VARCHAR(120) NOT NULL,
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX reservation_state_history_pax_idx ON reservation_state_history(booking_passenger_id, occurred_at DESC);

-- 6. Departure Charts & Manifests
CREATE TABLE reservation_charts (
    chart_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE RESTRICT,
    chart_status VARCHAR(24) NOT NULL DEFAULT 'PREPARED',
    total_passengers INT NOT NULL DEFAULT 0,
    confirmed_passengers INT NOT NULL DEFAULT 0,
    rac_passengers INT NOT NULL DEFAULT 0,
    waitlisted_passengers INT NOT NULL DEFAULT 0,
    current_version INT NOT NULL DEFAULT 1,
    prepared_by UUID REFERENCES users(user_id),
    prepared_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at TIMESTAMPTZ,
    finalized_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_chart_status_check CHECK (
        chart_status IN ('NOT_PREPARED', 'PREPARATION_PENDING', 'PREPARED', 'FINALIZED', 'SUPERSEDED')
    ),
    CONSTRAINT reservation_chart_unique_run UNIQUE (train_run_id)
);

CREATE INDEX reservation_charts_run_status_idx ON reservation_charts(train_run_id, chart_status);

CREATE TABLE reservation_chart_passengers (
    chart_passenger_id UUID PRIMARY KEY,
    chart_id UUID NOT NULL REFERENCES reservation_charts(chart_id) ON DELETE CASCADE,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id),
    pnr VARCHAR(24) NOT NULL,
    passenger_name VARCHAR(120) NOT NULL,
    coach_number VARCHAR(20),
    berth_number VARCHAR(20),
    berth_type VARCHAR(24),
    coach_class_code VARCHAR(32) NOT NULL,
    reservation_status VARCHAR(24) NOT NULL, -- CONFIRMED, RAC, WAITLISTED
    position_number INT,
    from_station_code VARCHAR(16) NOT NULL,
    to_station_code VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chart_passenger_unique UNIQUE (chart_id, booking_passenger_id)
);

CREATE INDEX chart_passengers_coach_idx ON reservation_chart_passengers(chart_id, coach_number, berth_number);
CREATE INDEX chart_passengers_pnr_idx ON reservation_chart_passengers(pnr);

CREATE TABLE reservation_chart_versions (
    version_id UUID PRIMARY KEY,
    chart_id UUID NOT NULL REFERENCES reservation_charts(chart_id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    snapshot_json TEXT NOT NULL,
    reason VARCHAR(120) NOT NULL,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chart_versions_unique UNIQUE (chart_id, version_number)
);

CREATE TABLE chart_correction_history (
    correction_id UUID PRIMARY KEY,
    chart_id UUID NOT NULL REFERENCES reservation_charts(chart_id) ON DELETE CASCADE,
    chart_passenger_id UUID REFERENCES reservation_chart_passengers(chart_passenger_id),
    previous_coach VARCHAR(20),
    new_coach VARCHAR(20),
    previous_berth VARCHAR(20),
    new_berth VARCHAR(20),
    reason TEXT NOT NULL,
    authorized_by UUID NOT NULL REFERENCES users(user_id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX chart_correction_chart_idx ON chart_correction_history(chart_id, occurred_at DESC);

-- 7. Rule Audit Logs
CREATE TABLE reservation_rule_audit_logs (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(48) NOT NULL,
    resource_type VARCHAR(48) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    details_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX reservation_rule_audit_time_idx ON reservation_rule_audit_logs(occurred_at DESC);

-- 8. Seed Default Quotas
INSERT INTO reservation_quotas (quota_id, quota_code, display_name, description, eligibility_criteria_json) VALUES
('11111111-1111-1111-1111-111111111001', 'GENERAL', 'General Quota', 'Standard public reservation quota with open booking access.', '{"all_eligible": true}'),
('11111111-1111-1111-1111-111111111002', 'TATKAL_STYLE', 'Tatkal-Style Premium', 'Short-notice emergency travel quota open within 24 hours of departure.', '{"window_hours": 24}'),
('11111111-1111-1111-1111-111111111003', 'LADIES', 'Ladies Quota', 'Dedicated quota for solo women or women traveling with children under 12.', '{"gender": "FEMALE"}'),
('11111111-1111-1111-1111-111111111004', 'SENIOR_CITIZEN', 'Senior Citizen Quota', 'Concessional quota for passengers aged 60 and above with priority lower berth allocation.', '{"min_age": 60}'),
('11111111-1111-1111-1111-111111111005', 'PERSONS_WITH_DISABILITIES', 'Divyangjan / PwD Quota', 'Specially adapted quota for persons with disabilities and escort.', '{"requires_certificate": true}'),
('11111111-1111-1111-1111-111111111006', 'DEFENCE', 'Defence Personnel Quota', 'Dedicated quota for serving military and paramilitary personnel.', '{"requires_defence_id": true}'),
('11111111-1111-1111-1111-111111111007', 'STAFF', 'Railway Duty & Staff Quota', 'Operational passes and railway administrative staff on duty.', '{"duty_pass": true}'),
('11111111-1111-1111-1111-111111111008', 'PREMIUM', 'Dynamic Premium Quota', 'Dynamic pricing quota with flexible booking allocations.', '{"all_eligible": true}'),
('11111111-1111-1111-1111-111111111009', 'SPECIAL', 'Special Tourist Quota', 'Special tourist and seasonal holiday allocations.', '{"all_eligible": true}'),
('11111111-1111-1111-1111-111111111010', 'CUSTOM', 'Custom Route Quota', 'Custom quota for high-demand festival and corridor trains.', '{"all_eligible": true}')
ON CONFLICT (quota_code) DO NOTHING;

-- 9. RBAC Permissions for Advanced Reservations & Charting
INSERT INTO permissions (permission_code, description) VALUES
('quotas:manage', 'Configure reservation quotas, policy versions, and inventory caps'),
('quotas:read', 'View configured reservation quotas and allocation percentages'),
('charts:prepare', 'Initiate departure chart preparation and RAC/WL promotions'),
('charts:finalize', 'Finalize and freeze departure reservation charts'),
('charts:correct', 'Authorize post-chart corrections and berth reassignments'),
('charts:read', 'Inspect passenger reservation charts and coach manifests')
ON CONFLICT (permission_code) DO NOTHING;

-- Grant permissions to appropriate roles
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', p.permission_code
FROM permissions p
WHERE p.permission_code IN ('quotas:manage', 'quotas:read', 'charts:prepare', 'charts:finalize', 'charts:correct', 'charts:read')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'RAILWAY_ADMIN', p.permission_code
FROM permissions p
WHERE p.permission_code IN ('quotas:manage', 'quotas:read', 'charts:prepare', 'charts:finalize', 'charts:correct', 'charts:read')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'OPERATIONS_MANAGER', p.permission_code
FROM permissions p
WHERE p.permission_code IN ('charts:prepare', 'charts:finalize', 'charts:correct', 'charts:read')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'STATION_MANAGER', p.permission_code
FROM permissions p
WHERE p.permission_code IN ('charts:read', 'charts:correct')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'RESERVATION_OFFICER', p.permission_code
FROM permissions p
WHERE p.permission_code IN ('quotas:read', 'charts:read')
ON CONFLICT DO NOTHING;
