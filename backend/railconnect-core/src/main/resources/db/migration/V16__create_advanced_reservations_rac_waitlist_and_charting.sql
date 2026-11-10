-- ============================================================================
-- RailConnect Phase 12 Database Migration: V16__create_advanced_reservations_rac_waitlist_and_charting.sql
-- Description: Configurable Quota Management, RAC-style queues, Waitlists,
--              Cancellation-driven atomic promotion, Departure chart preparation,
--              and operational manifest corrections.
-- ============================================================================

-- 1. Quota Definitions and Policy Versioning
CREATE TABLE IF NOT EXISTS reservation_quotas (
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

CREATE TABLE IF NOT EXISTS quota_policy_versions (
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

CREATE UNIQUE INDEX IF NOT EXISTS quota_policy_one_published_idx ON quota_policy_versions(status) WHERE status = 'PUBLISHED';

-- 2. Quota Inventory Allocations per Train Run and Class
CREATE TABLE IF NOT EXISTS quota_inventory_allocations (
    allocation_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL REFERENCES reservation_quotas(quota_code),
    allocated_berths INT NOT NULL DEFAULT 0,
    rac_capacity INT NOT NULL DEFAULT 0,
    waitlist_capacity INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quota_inventory_allocations_unique UNIQUE (train_run_id, coach_class_code, quota_code),
    CONSTRAINT quota_inventory_capacities_positive CHECK (
        allocated_berths >= 0 AND rac_capacity >= 0 AND waitlist_capacity >= 0
    )
);

CREATE INDEX IF NOT EXISTS quota_inventory_allocations_run_idx ON quota_inventory_allocations(train_run_id, coach_class_code);

-- 3. Queues & Waitlist Mechanics
CREATE TABLE IF NOT EXISTS reservation_queues (
    queue_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL REFERENCES reservation_quotas(quota_code),
    queue_type VARCHAR(16) NOT NULL, -- 'WAITLIST' or 'RAC'
    current_length INT NOT NULL DEFAULT 0,
    last_assigned_number INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_queues_type_check CHECK (queue_type IN ('WAITLIST', 'RAC')),
    CONSTRAINT reservation_queues_unique UNIQUE (train_run_id, coach_class_code, quota_code, queue_type)
);

CREATE TABLE IF NOT EXISTS waitlist_entries (
    waitlist_id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES reservation_queues(queue_id) ON DELETE CASCADE,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id) ON DELETE CASCADE,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL REFERENCES reservation_quotas(quota_code),
    initial_position INT NOT NULL,
    current_position INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'WAITLISTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT waitlist_entries_status_check CHECK (
        status IN ('WAITLISTED', 'PROMOTED_TO_RAC', 'PROMOTED_TO_CONFIRMED', 'CANCELLED', 'DROPPED')
    ),
    CONSTRAINT waitlist_positions_positive CHECK (initial_position > 0 AND current_position >= 0)
);

CREATE INDEX IF NOT EXISTS waitlist_entries_queue_idx ON waitlist_entries(queue_id, current_position) WHERE status = 'WAITLISTED';
CREATE INDEX IF NOT EXISTS waitlist_entries_booking_idx ON waitlist_entries(booking_id);
CREATE INDEX IF NOT EXISTS waitlist_entries_passenger_idx ON waitlist_entries(booking_passenger_id);

-- 4. RAC (Reservation Against Cancellation) Mechanics
CREATE TABLE IF NOT EXISTS rac_entries (
    rac_id UUID PRIMARY KEY,
    queue_id UUID NOT NULL REFERENCES reservation_queues(queue_id) ON DELETE CASCADE,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id) ON DELETE CASCADE,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL REFERENCES reservation_quotas(quota_code),
    rac_number INT NOT NULL,
    current_position INT NOT NULL,
    assigned_coach_number VARCHAR(16),
    assigned_berth_number VARCHAR(16),
    shared_berth_slot INT NOT NULL DEFAULT 1, -- 1 = First Half Seat, 2 = Second Half Seat
    status VARCHAR(24) NOT NULL DEFAULT 'RAC',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rac_entries_status_check CHECK (
        status IN ('RAC', 'PROMOTED_TO_CONFIRMED', 'CANCELLED')
    ),
    CONSTRAINT rac_positions_positive CHECK (rac_number > 0 AND current_position >= 0)
);

CREATE INDEX IF NOT EXISTS rac_entries_queue_idx ON rac_entries(queue_id, current_position) WHERE status = 'RAC';
CREATE INDEX IF NOT EXISTS rac_entries_booking_idx ON rac_entries(booking_id);
CREATE INDEX IF NOT EXISTS rac_entries_passenger_idx ON rac_entries(booking_passenger_id);

-- 5. Cancellation-Driven Atomic Promotion Tracking
CREATE TABLE IF NOT EXISTS reservation_promotions (
    promotion_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL,
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id) ON DELETE CASCADE,
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    freed_inventory_berth_id UUID REFERENCES inventory_berths(inventory_berth_id),
    allocated_coach_number VARCHAR(16),
    allocated_berth_number VARCHAR(16),
    triggered_by_cancellation_id UUID,
    promoted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reservation_promotion_status_check CHECK (
        (from_status = 'WAITLISTED' AND to_status IN ('RAC', 'CONFIRMED')) OR
        (from_status = 'RAC' AND to_status = 'CONFIRMED')
    )
);

CREATE INDEX IF NOT EXISTS reservation_promotions_run_idx ON reservation_promotions(train_run_id, promoted_at DESC);
CREATE INDEX IF NOT EXISTS reservation_promotions_passenger_idx ON reservation_promotions(booking_passenger_id);

CREATE TABLE IF NOT EXISTS passenger_reservation_state_history (
    history_id UUID PRIMARY KEY,
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id),
    previous_state VARCHAR(24) NOT NULL,
    new_state VARCHAR(24) NOT NULL,
    reason VARCHAR(120) NOT NULL,
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS passenger_reservation_state_history_pax_idx ON passenger_reservation_state_history(booking_passenger_id, occurred_at DESC);

-- 6. Departure Charts & Manifests
CREATE TABLE IF NOT EXISTS reservation_charts (
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
    CONSTRAINT reservation_charts_status_check CHECK (
        chart_status IN ('PREPARED', 'AMENDED', 'FINALIZED')
    ),
    CONSTRAINT reservation_charts_unique_run UNIQUE (train_run_id)
);

CREATE TABLE IF NOT EXISTS reservation_chart_passengers (
    chart_passenger_id UUID PRIMARY KEY,
    chart_id UUID NOT NULL REFERENCES reservation_charts(chart_id) ON DELETE CASCADE,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id),
    passenger_name VARCHAR(120) NOT NULL,
    age INT NOT NULL,
    gender VARCHAR(16) NOT NULL,
    booking_reference VARCHAR(32) NOT NULL,
    pnr VARCHAR(16),
    coach_class_code VARCHAR(32) NOT NULL,
    quota_code VARCHAR(32) NOT NULL,
    allocated_coach_number VARCHAR(16),
    allocated_berth_number VARCHAR(16),
    allocated_berth_type VARCHAR(16),
    final_reservation_status VARCHAR(24) NOT NULL,
    current_position INT NOT NULL DEFAULT 0,
    boarding_station_code VARCHAR(16) NOT NULL,
    destination_station_code VARCHAR(16) NOT NULL,
    chart_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chart_pax_status_check CHECK (final_reservation_status IN ('CONFIRMED', 'RAC', 'DROPPED_WAITLIST', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS chart_passengers_chart_idx ON reservation_chart_passengers(chart_id);
CREATE INDEX IF NOT EXISTS chart_passengers_coach_idx ON reservation_chart_passengers(chart_id, allocated_coach_number);

CREATE TABLE IF NOT EXISTS reservation_chart_versions (
    version_id UUID PRIMARY KEY,
    chart_id UUID NOT NULL REFERENCES reservation_charts(chart_id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    total_confirmed INT NOT NULL,
    total_rac INT NOT NULL,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chart_version_unique UNIQUE (chart_id, version_number)
);

-- 7. Post-Chart Correction History
CREATE TABLE IF NOT EXISTS chart_correction_history (
    correction_id UUID PRIMARY KEY,
    chart_id UUID NOT NULL REFERENCES reservation_charts(chart_id) ON DELETE CASCADE,
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id),
    old_coach_number VARCHAR(16),
    old_berth_number VARCHAR(16),
    new_coach_number VARCHAR(16) NOT NULL,
    new_berth_number VARCHAR(16) NOT NULL,
    correction_reason VARCHAR(255) NOT NULL,
    authorized_by UUID NOT NULL REFERENCES users(user_id),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS chart_corrections_chart_idx ON chart_correction_history(chart_id);

CREATE TABLE IF NOT EXISTS reservation_rule_audit_logs (
    audit_id UUID PRIMARY KEY,
    train_run_id UUID REFERENCES inventory_train_runs(train_run_id),
    event_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    details TEXT,
    actor_id UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS reservation_rule_audit_run_idx ON reservation_rule_audit_logs(train_run_id, created_at DESC);

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
