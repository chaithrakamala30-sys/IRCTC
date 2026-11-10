-- ============================================================================
-- RailConnect Phase 13 Database Migration: V17__create_railway_operations_control_centre.sql
-- Description: Railway Operations & Control Centre schema supporting station-based
--              train tracking, delay propagation, platform management,
--              operational disruptions, incident tracking, announcements,
--              and passenger impact assessment.
-- ============================================================================

-- 1. Station Platforms Master Table
CREATE TABLE station_platforms (
    platform_id UUID PRIMARY KEY,
    station_id UUID NOT NULL REFERENCES stations(station_id) ON DELETE CASCADE,
    platform_number VARCHAR(16) NOT NULL,
    track_number VARCHAR(16),
    length_meters INT DEFAULT 600,
    electrified BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(24) NOT NULL DEFAULT 'OPERATIONAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT station_platforms_unique_num UNIQUE (station_id, platform_number),
    CONSTRAINT station_platforms_status_check CHECK (status IN ('OPERATIONAL', 'MAINTENANCE', 'BLOCKED', 'DECOMMISSIONED'))
);

CREATE INDEX station_platforms_station_idx ON station_platforms(station_id, status);

-- 2. Train Run Operations (Tracks live day-of-service operational lifecycle)
CREATE TABLE train_run_operations (
    operation_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL UNIQUE REFERENCES inventory_train_runs(train_run_id) ON DELETE RESTRICT,
    operational_status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
    current_station_id UUID REFERENCES stations(station_id),
    next_station_id UUID REFERENCES stations(station_id),
    last_reported_station_id UUID REFERENCES stations(station_id),
    last_reported_event VARCHAR(24), -- 'ARRIVAL', 'DEPARTURE', 'PASSING'
    last_reported_at TIMESTAMPTZ,
    current_delay_minutes INT NOT NULL DEFAULT 0,
    route_progress_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    assigned_crew_notes TEXT,
    updated_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT train_run_operational_status_check CHECK (
        operational_status IN (
            'SCHEDULED', 'PREPARATION', 'READY', 'RUNNING', 'DELAYED',
            'HALTED', 'DIVERTED', 'CANCELLED', 'COMPLETED'
        )
    ),
    CONSTRAINT train_run_progress_bounds CHECK (route_progress_percent >= 0.00 AND route_progress_percent <= 100.00)
);

CREATE INDEX train_run_operations_status_idx ON train_run_operations(operational_status, current_delay_minutes);

CREATE TABLE train_run_status_history (
    history_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES train_run_operations(operation_id) ON DELETE CASCADE,
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    reported_station_id UUID REFERENCES stations(station_id),
    delay_minutes INT NOT NULL DEFAULT 0,
    remarks TEXT,
    actor_user_id UUID REFERENCES users(user_id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX train_run_status_history_op_idx ON train_run_status_history(operation_id, occurred_at DESC);

-- 3. Station Operational Updates (The core of station-based train tracking)
CREATE TABLE station_operational_updates (
    update_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    station_id UUID NOT NULL REFERENCES stations(station_id) ON DELETE RESTRICT,
    stop_index INT NOT NULL,
    event_type VARCHAR(24) NOT NULL, -- 'ARRIVAL', 'DEPARTURE', 'PASSING'
    scheduled_time TIMESTAMPTZ NOT NULL,
    reported_actual_time TIMESTAMPTZ NOT NULL,
    delay_minutes INT NOT NULL DEFAULT 0,
    platform_number VARCHAR(16),
    halt_duration_minutes INT DEFAULT 0,
    remarks TEXT,
    reported_by UUID NOT NULL REFERENCES users(user_id),
    verified BOOLEAN NOT NULL DEFAULT TRUE,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT station_updates_event_check CHECK (event_type IN ('ARRIVAL', 'DEPARTURE', 'PASSING')),
    CONSTRAINT station_updates_unique_event UNIQUE (train_run_id, station_id, event_type)
);

CREATE INDEX station_operational_updates_run_idx ON station_operational_updates(train_run_id, stop_index, reported_at DESC);
CREATE INDEX station_operational_updates_station_idx ON station_operational_updates(station_id, reported_at DESC);

-- 4. Platform Assignments and Occupancy Tracking
CREATE TABLE platform_assignments (
    assignment_id UUID PRIMARY KEY,
    station_id UUID NOT NULL REFERENCES stations(station_id) ON DELETE RESTRICT,
    platform_id UUID NOT NULL REFERENCES station_platforms(platform_id) ON DELETE RESTRICT,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    scheduled_arrival TIMESTAMPTZ NOT NULL,
    scheduled_departure TIMESTAMPTZ NOT NULL,
    actual_arrival TIMESTAMPTZ,
    actual_departure TIMESTAMPTZ,
    assignment_status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    conflict_flag BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT platform_assignments_status_check CHECK (
        assignment_status IN ('PLANNED', 'OCCUPIED', 'CLEARED', 'CANCELLED', 'CONFLICT')
    ),
    CONSTRAINT platform_assignments_time_check CHECK (scheduled_departure >= scheduled_arrival)
);

CREATE INDEX platform_assignments_slot_idx ON platform_assignments(station_id, platform_id, scheduled_arrival, scheduled_departure);
CREATE INDEX platform_assignments_run_idx ON platform_assignments(train_run_id);

-- 5. Operational Disruptions
CREATE TABLE operational_disruptions (
    disruption_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    disruption_type VARCHAR(32) NOT NULL, -- 'DELAY', 'TEMPORARY_HALT', 'DIVERSION', 'STATION_SKIPPED', 'PLATFORM_SHIFT', 'TRAIN_CANCELLED', 'RESTORED'
    affected_station_id UUID REFERENCES stations(station_id),
    start_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expected_duration_minutes INT,
    resolved_at TIMESTAMPTZ,
    reason TEXT NOT NULL,
    operational_impact TEXT,
    reported_by UUID NOT NULL REFERENCES users(user_id),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT operational_disruptions_status_check CHECK (status IN ('ACTIVE', 'RESOLVED', 'SUPERSEDED'))
);

CREATE INDEX operational_disruptions_run_status_idx ON operational_disruptions(train_run_id, status);

-- 6. Passenger Impact Assessments
CREATE TABLE passenger_impact_assessments (
    assessment_id UUID PRIMARY KEY,
    disruption_id UUID NOT NULL REFERENCES operational_disruptions(disruption_id) ON DELETE CASCADE,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    affected_passengers_count INT NOT NULL DEFAULT 0,
    affected_confirmed_count INT NOT NULL DEFAULT 0,
    affected_rac_count INT NOT NULL DEFAULT 0,
    affected_waitlist_count INT NOT NULL DEFAULT 0,
    summary TEXT NOT NULL,
    action_taken TEXT,
    assessed_by UUID NOT NULL REFERENCES users(user_id),
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX passenger_impact_disruption_idx ON passenger_impact_assessments(disruption_id);

-- 7. Operational Incidents
CREATE TABLE operational_incidents (
    incident_id UUID PRIMARY KEY,
    incident_number VARCHAR(32) NOT NULL UNIQUE,
    category VARCHAR(32) NOT NULL, -- 'BREAKDOWN', 'TECHNICAL_ISSUE', 'STATION_ISSUE', 'SAFETY_INCIDENT', 'ROUTE_DISRUPTION', 'PLATFORM_CONFLICT', 'SERVICE_DELAY', 'COMMUNICATION'
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM', -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'ESCALATED', 'RESOLVED', 'CLOSED'
    train_run_id UUID REFERENCES inventory_train_runs(train_run_id),
    station_id UUID REFERENCES stations(station_id),
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    resolution_notes TEXT,
    reported_by UUID NOT NULL REFERENCES users(user_id),
    assigned_to UUID REFERENCES users(user_id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT operational_incidents_status_check CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'ESCALATED', 'RESOLVED', 'CLOSED')
    ),
    CONSTRAINT operational_incidents_priority_check CHECK (
        priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    )
);

CREATE INDEX operational_incidents_status_priority_idx ON operational_incidents(status, priority, created_at DESC);

CREATE TABLE operational_incident_history (
    history_id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES operational_incidents(incident_id) ON DELETE CASCADE,
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    remarks TEXT NOT NULL,
    updated_by UUID NOT NULL REFERENCES users(user_id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX operational_incident_history_idx ON operational_incident_history(incident_id, occurred_at DESC);

-- 8. Operational Announcements
CREATE TABLE operational_announcements (
    announcement_id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    announcement_type VARCHAR(32) NOT NULL, -- 'DELAY', 'PLATFORM_CHANGE', 'ARRIVAL_DEPARTURE', 'DIVERSION', 'CANCELLATION', 'STATION_NOTICE', 'GENERAL'
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL', -- 'NORMAL', 'URGENT', 'CRITICAL'
    target_scope VARCHAR(16) NOT NULL DEFAULT 'ALL', -- 'ALL', 'TRAIN', 'STATION'
    target_train_run_id UUID REFERENCES inventory_train_runs(train_run_id),
    target_station_id UUID REFERENCES stations(station_id),
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED', -- 'DRAFT', 'PUBLISHED', 'EXPIRED', 'ARCHIVED'
    starts_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    published_by UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT announcements_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'EXPIRED', 'ARCHIVED')),
    CONSTRAINT announcements_scope_check CHECK (target_scope IN ('ALL', 'TRAIN', 'STATION'))
);

CREATE INDEX operational_announcements_active_idx ON operational_announcements(status, starts_at, expires_at);

-- 9. Control Centre Audit Logs
CREATE TABLE operational_control_audit_logs (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(user_id),
    action_type VARCHAR(48) NOT NULL,
    resource_type VARCHAR(48) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    details_json TEXT NOT NULL,
    ip_address VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX operational_control_audit_idx ON operational_control_audit_logs(occurred_at DESC);

-- 10. Seed Initial Station Platforms for Key Hubs
INSERT INTO station_platforms (platform_id, station_id, platform_number, track_number, length_meters, electrified, status)
SELECT gen_random_uuid(), s.station_id, p.platform_num, p.platform_num, 650, TRUE, 'OPERATIONAL'
FROM stations s
CROSS JOIN (VALUES ('1'), ('2'), ('3'), ('4'), ('5'), ('6')) AS p(platform_num)
WHERE s.station_code IN ('NDLS', 'HWH', 'CSMT', 'MAS', 'SBC', 'ADI')
ON CONFLICT (station_id, platform_number) DO NOTHING;

-- 11. RBAC Permissions for Operations & Control Centre
INSERT INTO permissions (permission_code, description) VALUES
('operations:control', 'Full operations control centre management and dashboard access'),
('operations:station:update', 'Record station arrival, departure, and passing updates'),
('operations:platform:manage', 'Manage station platforms and platform assignments'),
('operations:disruption:manage', 'Report and resolve operational delays, diversions, and halts'),
('operations:incident:manage', 'Create, escalate, and resolve operational incidents'),
('operations:announcement:publish', 'Publish operational bulletins and station announcements'),
('operations:tracking:read', 'Access internal and public train tracking telemetry')
ON CONFLICT (permission_code) DO NOTHING;

-- Role grants
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', p.permission_code
FROM permissions p
WHERE p.permission_code LIKE 'operations:%'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'OPERATIONS_MANAGER', p.permission_code
FROM permissions p
WHERE p.permission_code LIKE 'operations:%'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'STATION_MANAGER', p.permission_code
FROM permissions p
WHERE p.permission_code IN (
    'operations:station:update', 'operations:platform:manage',
    'operations:incident:manage', 'operations:announcement:publish',
    'operations:tracking:read'
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'RAILWAY_ADMIN', p.permission_code
FROM permissions p
WHERE p.permission_code LIKE 'operations:%'
ON CONFLICT DO NOTHING;
