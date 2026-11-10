-- ==========================================================================
-- RAILCONNECT COMPLETE DATABASE MIGRATION SCRIPT (V1 to V14)
-- Target Schema: railconnect
-- ==========================================================================

CREATE SCHEMA IF NOT EXISTS railconnect;
SET search_path TO railconnect, public;


-- ==========================================================================
-- Migration: V1__create_application_metadata.sql
-- ==========================================================================

CREATE TABLE app_metadata (
    metadata_key VARCHAR(100) PRIMARY KEY,
    metadata_value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);



-- ==========================================================================
-- Migration: V2__create_identity_and_access_management.sql
-- ==========================================================================

CREATE TABLE users (
    user_id UUID PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    mobile_number VARCHAR(16) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    email_verified_at TIMESTAMPTZ,
    mobile_verified_at TIMESTAMPTZ,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    terms_version VARCHAR(40) NOT NULL,
    privacy_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_mobile_unique UNIQUE (mobile_number)
);

CREATE TABLE roles (
    role_code VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    system_managed BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE permissions (
    permission_code VARCHAR(80) PRIMARY KEY,
    description VARCHAR(200) NOT NULL
);

CREATE TABLE role_permissions (
    role_code VARCHAR(40) NOT NULL REFERENCES roles(role_code) ON DELETE CASCADE,
    permission_code VARCHAR(80) NOT NULL REFERENCES permissions(permission_code) ON DELETE CASCADE,
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_code VARCHAR(40) NOT NULL REFERENCES roles(role_code),
    scope_type VARCHAR(24) NOT NULL DEFAULT 'GLOBAL',
    scope_id VARCHAR(100) NOT NULL DEFAULT '*',
    assigned_by UUID REFERENCES users(user_id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_code, scope_type, scope_id),
    CONSTRAINT user_role_scope_check CHECK (
        (scope_type = 'GLOBAL' AND scope_id = '*') OR
        (scope_type <> 'GLOBAL' AND scope_id <> '*')
    )
);

CREATE INDEX user_roles_user_idx ON user_roles(user_id);

CREATE TABLE user_sessions (
    session_id VARCHAR(128) PRIMARY KEY,
    public_session_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    authenticated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    user_agent VARCHAR(512),
    remote_address_hash CHAR(64)
);

CREATE INDEX user_sessions_user_active_idx ON user_sessions(user_id, revoked_at);

CREATE TABLE authentication_events (
    event_id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(user_id),
    identifier_hash CHAR(64),
    event_type VARCHAR(48) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    remote_address_hash CHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT authentication_event_outcome_check CHECK (outcome IN ('SUCCESS', 'FAILURE', 'BLOCKED'))
);

CREATE INDEX authentication_events_user_time_idx
    ON authentication_events(user_id, occurred_at DESC);
CREATE INDEX authentication_events_type_time_idx
    ON authentication_events(event_type, occurred_at DESC);

CREATE TABLE login_throttles (
    identifier_hash CHAR(64) PRIMARY KEY,
    failure_count INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT login_throttles_failure_count_check CHECK (failure_count >= 0)
);

CREATE TABLE registration_throttles (
    remote_address_hash CHAR(64) PRIMARY KEY,
    attempt_count INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT registration_throttles_attempt_count_check CHECK (attempt_count >= 0)
);

CREATE TABLE password_reset_tokens (
    token_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE mfa_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    secret_ciphertext BYTEA NOT NULL,
    encryption_key_version VARCHAR(32) NOT NULL,
    enabled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recovery_codes (
    recovery_code_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT recovery_code_user_hash_unique UNIQUE (user_id, code_hash)
);

CREATE TABLE security_audit_logs (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(user_id),
    target_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT security_audit_outcome_check CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX security_audit_logs_time_idx ON security_audit_logs(occurred_at DESC);
CREATE INDEX security_audit_logs_target_time_idx
    ON security_audit_logs(target_user_id, occurred_at DESC);

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INTEGER NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK
        FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

INSERT INTO roles (role_code, display_name) VALUES
    ('PASSENGER', 'Passenger'),
    ('SUPER_ADMIN', 'Super Administrator'),
    ('RAILWAY_ADMIN', 'Railway Administrator'),
    ('STATION_MANAGER', 'Station Manager'),
    ('RESERVATION_OFFICER', 'Reservation Officer'),
    ('FINANCE_ADMIN', 'Finance Administrator'),
    ('CUSTOMER_SUPPORT', 'Customer Support');

INSERT INTO permissions (permission_code, description) VALUES
    ('identity:self:read', 'Read own identity profile'),
    ('identity:self:update', 'Update permitted own profile fields'),
    ('identity:self:password', 'Change own password'),
    ('identity:self:sessions', 'View and revoke own sessions'),
    ('identity:users:read', 'Read identity account records'),
    ('identity:users:create', 'Provision staff identity accounts'),
    ('identity:users:status', 'Change identity account status'),
    ('identity:users:roles', 'Assign identity roles'),
    ('identity:roles:read', 'Read configured roles and permissions'),
    ('security:audit:read', 'Read security audit records'),
    ('operations:catalogue:manage', 'Manage railway catalogue resources'),
    ('operations:station:read', 'Read assigned station operations'),
    ('operations:station:manage', 'Manage assigned station operations'),
    ('reservations:manage', 'Manage authorized reservation functions'),
    ('finance:reconciliation:read', 'Read authorized reconciliation data'),
    ('support:bookings:investigate', 'Investigate authorized booking issues');

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'PASSENGER', permission_code FROM permissions
WHERE permission_code IN (
    'identity:self:read', 'identity:self:update',
    'identity:self:password', 'identity:self:sessions'
);

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', permission_code FROM permissions;

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('RAILWAY_ADMIN', 'operations:catalogue:manage'),
    ('RAILWAY_ADMIN', 'identity:roles:read'),
    ('STATION_MANAGER', 'operations:station:read'),
    ('STATION_MANAGER', 'operations:station:manage'),
    ('RESERVATION_OFFICER', 'reservations:manage'),
    ('FINANCE_ADMIN', 'finance:reconciliation:read'),
    ('CUSTOMER_SUPPORT', 'support:bookings:investigate');



-- ==========================================================================
-- Migration: V3__delegate_credentials_to_supabase_auth.sql
-- ==========================================================================

ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;



-- ==========================================================================
-- Migration: V4__link_legacy_users_to_supabase_auth.sql
-- ==========================================================================

ALTER TABLE users
    ADD COLUMN auth_user_id UUID;

CREATE UNIQUE INDEX users_auth_user_id_unique
    ON users (auth_user_id)
    WHERE auth_user_id IS NOT NULL;



-- ==========================================================================
-- Migration: V5__create_railway_master_data.sql
-- ==========================================================================

CREATE TABLE railway_zones (
    zone_id UUID PRIMARY KEY,
    zone_code VARCHAR(24) NOT NULL UNIQUE,
    zone_name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT railway_zones_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE railway_divisions (
    division_id UUID PRIMARY KEY,
    zone_id UUID NOT NULL REFERENCES railway_zones(zone_id),
    division_code VARCHAR(24) NOT NULL UNIQUE,
    division_name VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT railway_divisions_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX railway_divisions_zone_idx ON railway_divisions(zone_id, status);

CREATE TABLE stations (
    station_id UUID PRIMARY KEY,
    station_code VARCHAR(20) NOT NULL UNIQUE,
    station_name VARCHAR(160) NOT NULL,
    alternate_names JSONB NOT NULL DEFAULT '[]'::JSONB,
    city VARCHAR(120) NOT NULL,
    state VARCHAR(120) NOT NULL,
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    address TEXT,
    zone_id UUID REFERENCES railway_zones(zone_id),
    division_id UUID REFERENCES railway_divisions(division_id),
    station_category VARCHAR(40),
    code_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by UUID REFERENCES users(user_id),
    updated_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stations_coordinates_pair_check CHECK (
        (latitude IS NULL AND longitude IS NULL) OR
        (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT stations_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED')),
    CONSTRAINT stations_alternate_names_array_check CHECK (jsonb_typeof(alternate_names) = 'array')
);

CREATE INDEX stations_city_state_status_idx ON stations(lower(city), lower(state), status);
CREATE INDEX stations_name_idx ON stations(lower(station_name));

CREATE TABLE station_facilities (
    station_id UUID NOT NULL REFERENCES stations(station_id),
    facility_code VARCHAR(40) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (station_id, facility_code),
    CONSTRAINT station_facilities_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE TABLE station_versions (
    station_version_id UUID PRIMARY KEY,
    station_id UUID NOT NULL REFERENCES stations(station_id),
    version_number INTEGER NOT NULL,
    version_type VARCHAR(24) NOT NULL,
    snapshot JSONB NOT NULL,
    change_reason VARCHAR(500),
    changed_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT station_versions_number_unique UNIQUE (station_id, version_number),
    CONSTRAINT station_versions_type_check
        CHECK (version_type IN ('CREATED', 'UPDATED', 'STATUS_CHANGED', 'VERIFIED')),
    CONSTRAINT station_versions_snapshot_check CHECK (jsonb_typeof(snapshot) = 'object')
);

CREATE INDEX station_versions_history_idx ON station_versions(station_id, version_number DESC);

CREATE TABLE train_categories (
    category_id UUID PRIMARY KEY,
    category_code VARCHAR(40) NOT NULL UNIQUE,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(400),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE train_types (
    train_type_id UUID PRIMARY KEY,
    type_code VARCHAR(40) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(400),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE train_routes (
    route_id UUID PRIMARY KEY,
    route_name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by UUID REFERENCES users(user_id),
    updated_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT train_routes_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
);

CREATE INDEX train_routes_status_name_idx ON train_routes(status, lower(route_name));

CREATE TABLE train_route_versions (
    route_version_id UUID PRIMARY KEY,
    route_id UUID NOT NULL REFERENCES train_routes(route_id),
    version_number INTEGER NOT NULL,
    source_station_id UUID NOT NULL REFERENCES stations(station_id),
    destination_station_id UUID NOT NULL REFERENCES stations(station_id),
    total_distance_km NUMERIC(9, 2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    change_reason VARCHAR(500),
    created_by UUID REFERENCES users(user_id),
    published_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT train_route_versions_number_unique UNIQUE (route_id, version_number),
    CONSTRAINT train_route_versions_distance_check CHECK (total_distance_km > 0),
    CONSTRAINT train_route_versions_dates_check CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT train_route_versions_stations_check CHECK (source_station_id <> destination_station_id),
    CONSTRAINT train_route_versions_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'RETIRED'))
);

CREATE INDEX train_route_versions_effective_idx
    ON train_route_versions(route_id, status, effective_from, effective_to);

CREATE TABLE train_route_stops (
    route_stop_id UUID PRIMARY KEY,
    route_version_id UUID NOT NULL REFERENCES train_route_versions(route_version_id),
    station_id UUID NOT NULL REFERENCES stations(station_id),
    stop_sequence INTEGER NOT NULL,
    arrival_time TIME,
    departure_time TIME,
    arrival_day_offset SMALLINT NOT NULL DEFAULT 0,
    departure_day_offset SMALLINT NOT NULL DEFAULT 0,
    halt_minutes INTEGER NOT NULL DEFAULT 0,
    distance_from_source_km NUMERIC(9, 2) NOT NULL,
    stop_type VARCHAR(24) NOT NULL DEFAULT 'REGULAR',
    platform_label VARCHAR(24),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT train_route_stops_sequence_unique UNIQUE (route_version_id, stop_sequence),
    CONSTRAINT train_route_stops_station_unique UNIQUE (route_version_id, station_id),
    CONSTRAINT train_route_stops_sequence_check CHECK (stop_sequence > 0),
    CONSTRAINT train_route_stops_offsets_check CHECK (
        arrival_day_offset >= 0 AND departure_day_offset >= arrival_day_offset
    ),
    CONSTRAINT train_route_stops_time_order_check CHECK (
        arrival_time IS NULL OR departure_time IS NULL OR
        departure_day_offset > arrival_day_offset OR
        (departure_day_offset = arrival_day_offset AND departure_time >= arrival_time)
    ),
    CONSTRAINT train_route_stops_halt_check CHECK (halt_minutes >= 0),
    CONSTRAINT train_route_stops_distance_check CHECK (distance_from_source_km >= 0),
    CONSTRAINT train_route_stops_type_check CHECK (stop_type IN ('ORIGIN', 'REGULAR', 'TERMINAL'))
);

CREATE INDEX train_route_stops_station_idx ON train_route_stops(station_id, route_version_id);

CREATE TABLE trains (
    train_id UUID PRIMARY KEY,
    train_number VARCHAR(32) NOT NULL UNIQUE,
    train_name VARCHAR(160) NOT NULL,
    category_id UUID NOT NULL REFERENCES train_categories(category_id),
    train_type_id UUID NOT NULL REFERENCES train_types(train_type_id),
    source_station_id UUID NOT NULL REFERENCES stations(station_id),
    destination_station_id UUID NOT NULL REFERENCES stations(station_id),
    route_id UUID REFERENCES train_routes(route_id),
    total_distance_km NUMERIC(9, 2),
    description VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_by UUID REFERENCES users(user_id),
    updated_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT trains_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')),
    CONSTRAINT trains_stations_check CHECK (source_station_id <> destination_station_id),
    CONSTRAINT trains_distance_check CHECK (total_distance_km IS NULL OR total_distance_km > 0),
    CONSTRAINT trains_effective_dates_check CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX trains_name_status_idx ON trains(lower(train_name), status);
CREATE INDEX trains_category_status_idx ON trains(category_id, status);
CREATE INDEX trains_station_pair_idx ON trains(source_station_id, destination_station_id, status);

CREATE TABLE train_schedules (
    schedule_id UUID PRIMARY KEY,
    train_id UUID NOT NULL REFERENCES trains(train_id),
    route_version_id UUID NOT NULL REFERENCES train_route_versions(route_version_id),
    version_number INTEGER NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    change_reason VARCHAR(500),
    created_by UUID REFERENCES users(user_id),
    published_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT train_schedules_version_unique UNIQUE (train_id, version_number),
    CONSTRAINT train_schedules_dates_check CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT train_schedules_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUSPENDED', 'SUPERSEDED', 'RETIRED'))
);

CREATE INDEX train_schedules_effective_idx
    ON train_schedules(train_id, status, effective_from, effective_to);

CREATE TABLE train_schedule_days (
    schedule_id UUID NOT NULL REFERENCES train_schedules(schedule_id),
    day_of_week SMALLINT NOT NULL,
    PRIMARY KEY (schedule_id, day_of_week),
    CONSTRAINT train_schedule_days_range_check CHECK (day_of_week BETWEEN 1 AND 7)
);

CREATE TABLE train_schedule_exceptions (
    schedule_id UUID NOT NULL REFERENCES train_schedules(schedule_id),
    service_date DATE NOT NULL,
    service_status VARCHAR(16) NOT NULL,
    change_reason VARCHAR(300),
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id, service_date),
    CONSTRAINT train_schedule_exceptions_status_check CHECK (service_status IN ('RUN', 'CANCEL'))
);

CREATE INDEX train_schedule_exceptions_date_idx ON train_schedule_exceptions(service_date, service_status);

CREATE TABLE coach_classes (
    coach_class_id UUID PRIMARY KEY,
    class_code VARCHAR(32) NOT NULL UNIQUE,
    class_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(400),
    accommodation_type VARCHAR(24) NOT NULL,
    fare_category VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT coach_classes_accommodation_check
        CHECK (accommodation_type IN ('SEATING', 'SLEEPING', 'MIXED', 'OTHER'))
);

CREATE TABLE coach_types (
    coach_type_id UUID PRIMARY KEY,
    type_code VARCHAR(32) NOT NULL UNIQUE,
    type_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(400),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE coach_configurations (
    configuration_id UUID PRIMARY KEY,
    configuration_code VARCHAR(40) NOT NULL UNIQUE,
    configuration_name VARCHAR(120) NOT NULL,
    coach_class_id UUID NOT NULL REFERENCES coach_classes(coach_class_id),
    coach_type_id UUID NOT NULL REFERENCES coach_types(coach_type_id),
    capacity INTEGER NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT coach_configurations_capacity_check CHECK (capacity > 0),
    CONSTRAINT coach_configurations_name_unique UNIQUE (configuration_name, coach_class_id, coach_type_id)
);

CREATE TABLE coaches (
    coach_id UUID PRIMARY KEY,
    coach_code VARCHAR(40) NOT NULL UNIQUE,
    configuration_id UUID NOT NULL REFERENCES coach_configurations(configuration_id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accommodation_layouts (
    layout_id UUID PRIMARY KEY,
    configuration_id UUID NOT NULL REFERENCES coach_configurations(configuration_id),
    version_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by UUID REFERENCES users(user_id),
    published_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT accommodation_layouts_version_unique UNIQUE (configuration_id, version_number),
    CONSTRAINT accommodation_layouts_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'RETIRED'))
);

CREATE TABLE physical_accommodations (
    accommodation_id UUID PRIMARY KEY,
    layout_id UUID NOT NULL REFERENCES accommodation_layouts(layout_id),
    accommodation_number VARCHAR(20) NOT NULL,
    accommodation_type VARCHAR(24) NOT NULL,
    position JSONB NOT NULL DEFAULT '{}'::JSONB,
    deck_level SMALLINT,
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT physical_accommodations_number_unique UNIQUE (layout_id, accommodation_number),
    CONSTRAINT physical_accommodations_order_unique UNIQUE (layout_id, display_order),
    CONSTRAINT physical_accommodations_type_check CHECK (
        accommodation_type IN ('SEAT', 'LOWER_BERTH', 'MIDDLE_BERTH', 'UPPER_BERTH',
                               'SIDE_LOWER_BERTH', 'SIDE_UPPER_BERTH', 'CHAIR', 'OTHER')
    ),
    CONSTRAINT physical_accommodations_position_check CHECK (jsonb_typeof(position) = 'object'),
    CONSTRAINT physical_accommodations_deck_check CHECK (deck_level IS NULL OR deck_level > 0),
    CONSTRAINT physical_accommodations_order_check CHECK (display_order > 0)
);

CREATE INDEX physical_accommodations_layout_idx ON physical_accommodations(layout_id, display_order);

CREATE TABLE train_compositions (
    composition_id UUID PRIMARY KEY,
    train_id UUID NOT NULL REFERENCES trains(train_id),
    current_version_id UUID,
    created_by UUID REFERENCES users(user_id),
    updated_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT train_compositions_train_unique UNIQUE (train_id)
);

CREATE TABLE train_composition_versions (
    composition_version_id UUID PRIMARY KEY,
    composition_id UUID NOT NULL REFERENCES train_compositions(composition_id),
    version_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    total_capacity INTEGER NOT NULL DEFAULT 0,
    snapshot JSONB NOT NULL DEFAULT '{}'::JSONB,
    change_reason VARCHAR(500),
    created_by UUID REFERENCES users(user_id),
    published_by UUID REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    CONSTRAINT train_composition_versions_number_unique UNIQUE (composition_id, version_number),
    CONSTRAINT train_composition_versions_capacity_check CHECK (total_capacity >= 0),
    CONSTRAINT train_composition_versions_snapshot_check CHECK (jsonb_typeof(snapshot) = 'object'),
    CONSTRAINT train_composition_versions_status_check
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'RETIRED'))
);

ALTER TABLE train_compositions
    ADD CONSTRAINT train_compositions_current_version_fk
    FOREIGN KEY (current_version_id) REFERENCES train_composition_versions(composition_version_id);

CREATE TABLE train_composition_coaches (
    composition_version_id UUID NOT NULL REFERENCES train_composition_versions(composition_version_id),
    coach_id UUID NOT NULL REFERENCES coaches(coach_id),
    coach_number VARCHAR(20) NOT NULL,
    position INTEGER NOT NULL,
    coach_capacity INTEGER NOT NULL,
    configuration_snapshot JSONB NOT NULL,
    PRIMARY KEY (composition_version_id, coach_id),
    CONSTRAINT train_composition_coaches_number_unique UNIQUE (composition_version_id, coach_number),
    CONSTRAINT train_composition_coaches_position_unique UNIQUE (composition_version_id, position),
    CONSTRAINT train_composition_coaches_position_check CHECK (position > 0),
    CONSTRAINT train_composition_coaches_capacity_check CHECK (coach_capacity > 0),
    CONSTRAINT train_composition_coaches_snapshot_check CHECK (jsonb_typeof(configuration_snapshot) = 'object')
);

CREATE TABLE master_data_audit_logs (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(user_id),
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    previous_value JSONB,
    new_value JSONB,
    change_reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT master_data_audit_action_check
        CHECK (action IN ('CREATED', 'UPDATED', 'STATUS_CHANGED', 'VERSION_CREATED', 'PUBLISHED', 'RETIRED'))
);

CREATE INDEX master_data_audit_entity_idx
    ON master_data_audit_logs(entity_type, entity_id, occurred_at DESC);
CREATE INDEX master_data_audit_actor_idx
    ON master_data_audit_logs(actor_user_id, occurred_at DESC);

INSERT INTO permissions (permission_code, description) VALUES
    ('operations:master-data:read', 'Read railway master data'),
    ('operations:stations:manage', 'Create and update railway stations'),
    ('operations:trains:manage', 'Create and update train definitions'),
    ('operations:routes:manage', 'Create, version, and publish train routes'),
    ('operations:schedules:manage', 'Create and update train schedules'),
    ('operations:composition:manage', 'Manage coach compositions and accommodation layouts'),
    ('operations:master-data:publish', 'Publish validated railway master data'),
    ('operations:master-data:audit:read', 'Read railway master-data audit history');

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', permission_code
FROM permissions
WHERE permission_code IN (
    'operations:master-data:read',
    'operations:stations:manage',
    'operations:trains:manage',
    'operations:routes:manage',
    'operations:schedules:manage',
    'operations:composition:manage',
    'operations:master-data:publish',
    'operations:master-data:audit:read'
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('RAILWAY_ADMIN', 'operations:master-data:read'),
    ('RAILWAY_ADMIN', 'operations:stations:manage'),
    ('RAILWAY_ADMIN', 'operations:trains:manage'),
    ('RAILWAY_ADMIN', 'operations:routes:manage'),
    ('RAILWAY_ADMIN', 'operations:schedules:manage'),
    ('RAILWAY_ADMIN', 'operations:composition:manage'),
    ('RAILWAY_ADMIN', 'operations:master-data:publish'),
    ('RAILWAY_ADMIN', 'operations:master-data:audit:read')
ON CONFLICT DO NOTHING;



-- ==========================================================================
-- Migration: V6__index_passenger_search.sql
-- ==========================================================================

CREATE INDEX stations_active_code_idx
    ON stations(lower(station_code))
    WHERE status = 'ACTIVE';

CREATE INDEX stations_active_search_vector_idx
    ON stations USING GIN (
        to_tsvector('simple', station_name || ' ' || city || ' ' || alternate_names::text)
    )
    WHERE status = 'ACTIVE';

CREATE INDEX train_schedules_passenger_search_idx
    ON train_schedules(train_id, effective_from, effective_to, route_version_id)
    WHERE status IN ('PUBLISHED', 'SUPERSEDED') AND published_at IS NOT NULL;

CREATE INDEX train_route_stops_station_version_idx
    ON train_route_stops(station_id, route_version_id, stop_sequence);



-- ==========================================================================
-- Migration: V7__allow_schedule_range_audit_action.sql
-- ==========================================================================

ALTER TABLE master_data_audit_logs
    DROP CONSTRAINT master_data_audit_action_check;

ALTER TABLE master_data_audit_logs
    ADD CONSTRAINT master_data_audit_action_check
        CHECK (action IN (
            'CREATED',
            'UPDATED',
            'STATUS_CHANGED',
            'VERSION_CREATED',
            'PUBLISHED',
            'RETIRED',
            'EFFECTIVE_RANGE_CLOSED'
        ));



-- ==========================================================================
-- Migration: V8__create_fare_engine.sql
-- ==========================================================================

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



-- ==========================================================================
-- Migration: V9__create_seat_inventory.sql
-- ==========================================================================

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



-- ==========================================================================
-- Migration: V10__create_reservation_allocation_engine.sql
-- ==========================================================================

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



-- ==========================================================================
-- Migration: V11__create_booking_management_engine.sql
-- ==========================================================================

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



-- ==========================================================================
-- Migration: V12__create_railconnect_pay_scaffolding.sql
-- ==========================================================================

CREATE TABLE payment_provider_configurations (
    provider_code VARCHAR(32) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    demo_mode BOOLEAN NOT NULL DEFAULT TRUE,
    contract_status VARCHAR(24) NOT NULL DEFAULT 'MISSING',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_provider_contract_status_check
        CHECK (contract_status IN ('MISSING', 'UNDER_REVIEW', 'VERIFIED')),
    CONSTRAINT payment_provider_enablement_check CHECK (
        (enabled = FALSE AND demo_mode = TRUE)
        OR (enabled = TRUE AND demo_mode = TRUE AND contract_status = 'VERIFIED')
    )
);

INSERT INTO payment_provider_configurations(provider_code, enabled, demo_mode, contract_status)
VALUES ('DHRUVA_BANK', FALSE, TRUE, 'MISSING')
ON CONFLICT (provider_code) DO NOTHING;

CREATE TABLE payment_request_rate_limits (
    owner_user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    CONSTRAINT payment_request_rate_count_check CHECK (request_count > 0)
);

ALTER TABLE bookings
    ADD CONSTRAINT bookings_id_owner_unique UNIQUE (booking_id, owner_user_id);

ALTER TABLE bookings
    ADD CONSTRAINT bookings_payment_snapshot_unique
        UNIQUE (booking_id, fare_quote_id, total_payable, currency);

CREATE TABLE payment_orders (
    payment_order_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    provider_code VARCHAR(32) NOT NULL REFERENCES payment_provider_configurations(provider_code),
    merchant_reference VARCHAR(80) NOT NULL UNIQUE,
    provider_payment_intent_id VARCHAR(128),
    provider_session_id VARCHAR(128),
    checkout_url TEXT,
    transaction_reference VARCHAR(128),
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    booking_amount_snapshot NUMERIC(14, 2) NOT NULL,
    fare_quote_id UUID NOT NULL REFERENCES fare_quotes(fare_quote_id),
    payment_status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    CONSTRAINT payment_order_amount_check CHECK (
        amount_minor > 0 AND booking_amount_snapshot > 0
    ),
    CONSTRAINT payment_order_currency_check CHECK (currency = 'INR'),
    CONSTRAINT payment_order_minor_units_check CHECK (
        booking_amount_snapshot * 100 = amount_minor
    ),
    CONSTRAINT payment_order_booking_owner_fk
        FOREIGN KEY (booking_id, owner_user_id) REFERENCES bookings(booking_id, owner_user_id),
    CONSTRAINT payment_order_booking_snapshot_fk
        FOREIGN KEY (booking_id, fare_quote_id, booking_amount_snapshot, currency)
        REFERENCES bookings(booking_id, fare_quote_id, total_payable, currency),
    CONSTRAINT payment_order_provider_pair_unique UNIQUE (payment_order_id, provider_code),
    CONSTRAINT payment_order_booking_pair_unique UNIQUE (payment_order_id, booking_id),
    CONSTRAINT payment_order_owner_pair_unique UNIQUE (payment_order_id, owner_user_id),
    CONSTRAINT payment_order_status_check CHECK (payment_status IN (
        'CREATED', 'INITIATED', 'CHECKOUT_PENDING', 'PROCESSING', 'SUCCEEDED',
        'FAILED', 'CANCELLED', 'EXPIRED', 'REQUIRES_RECONCILIATION', 'REVERSED'
    )),
    CONSTRAINT payment_order_expiry_check CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX payment_order_provider_intent_unique
    ON payment_orders(provider_code, provider_payment_intent_id)
    WHERE provider_payment_intent_id IS NOT NULL;
CREATE INDEX payment_order_owner_history_idx
    ON payment_orders(owner_user_id, created_at DESC, payment_order_id);
CREATE INDEX payment_order_expiration_idx
    ON payment_orders(expires_at, payment_order_id)
    WHERE payment_status IN ('CREATED', 'INITIATED', 'CHECKOUT_PENDING', 'PROCESSING',
                             'REQUIRES_RECONCILIATION');

CREATE TABLE payment_attempts (
    payment_attempt_id UUID PRIMARY KEY,
    payment_order_id UUID NOT NULL,
    attempt_number SMALLINT NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    provider_idempotency_key VARCHAR(100) NOT NULL,
    provider_payment_intent_id VARCHAR(128),
    provider_session_id VARCHAR(128),
    attempt_status VARCHAR(32) NOT NULL,
    retry_count SMALLINT NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    CONSTRAINT payment_attempt_number_check CHECK (attempt_number > 0),
    CONSTRAINT payment_attempt_retry_count_check CHECK (retry_count >= 0),
    CONSTRAINT payment_attempt_status_check CHECK (attempt_status IN (
        'CREATED', 'INITIATED', 'CHECKOUT_PENDING', 'PROCESSING', 'SUCCEEDED',
        'FAILED', 'CANCELLED', 'EXPIRED', 'REQUIRES_RECONCILIATION'
    )),
    CONSTRAINT payment_attempt_order_provider_fk
        FOREIGN KEY (payment_order_id, provider_code)
        REFERENCES payment_orders(payment_order_id, provider_code),
    CONSTRAINT payment_attempt_order_number_unique UNIQUE (payment_order_id, attempt_number)
);

CREATE UNIQUE INDEX payment_attempt_provider_intent_unique
    ON payment_attempts(provider_code, provider_payment_intent_id)
    WHERE provider_payment_intent_id IS NOT NULL;
CREATE UNIQUE INDEX payment_attempt_provider_idempotency_unique
    ON payment_attempts(provider_code, provider_idempotency_key);

CREATE TABLE payment_status_history (
    payment_status_history_id UUID PRIMARY KEY,
    payment_order_id UUID NOT NULL REFERENCES payment_orders(payment_order_id),
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT payment_history_source_check CHECK (
        source IN ('SYSTEM', 'PROVIDER_RESPONSE', 'VERIFIED_WEBHOOK', 'STATUS_QUERY',
                   'RECONCILIATION', 'EXPIRY_JOB')
    ),
    CONSTRAINT payment_history_new_status_check CHECK (new_status IN (
        'CREATED', 'INITIATED', 'CHECKOUT_PENDING', 'PROCESSING', 'SUCCEEDED',
        'FAILED', 'CANCELLED', 'EXPIRED', 'REQUIRES_RECONCILIATION', 'REVERSED'
    )),
    CONSTRAINT payment_history_previous_status_check CHECK (
        previous_status IS NULL OR previous_status IN (
            'CREATED', 'INITIATED', 'CHECKOUT_PENDING', 'PROCESSING', 'SUCCEEDED',
            'FAILED', 'CANCELLED', 'EXPIRED', 'REQUIRES_RECONCILIATION', 'REVERSED'
        )
    )
);

CREATE INDEX payment_status_history_order_idx
    ON payment_status_history(payment_order_id, occurred_at, payment_status_history_id);

CREATE TABLE payment_idempotency_records (
    payment_idempotency_record_id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    payment_order_id UUID UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_idempotency_owner_key_unique UNIQUE (owner_user_id, idempotency_key),
    CONSTRAINT payment_idempotency_key_check
        CHECK (length(btrim(idempotency_key)) BETWEEN 1 AND 100),
    CONSTRAINT payment_idempotency_fingerprint_check
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT payment_idempotency_order_owner_fk
        FOREIGN KEY (payment_order_id, owner_user_id)
        REFERENCES payment_orders(payment_order_id, owner_user_id)
);

CREATE TABLE payment_webhook_events (
    payment_webhook_event_id UUID PRIMARY KEY,
    provider_code VARCHAR(32) NOT NULL REFERENCES payment_provider_configurations(provider_code),
    payment_order_id UUID,
    provider_event_id VARCHAR(128) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    processing_status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    CONSTRAINT payment_webhook_provider_event_unique UNIQUE (provider_code, provider_event_id),
    CONSTRAINT payment_webhook_order_provider_fk
        FOREIGN KEY (payment_order_id, provider_code)
        REFERENCES payment_orders(payment_order_id, provider_code),
    CONSTRAINT payment_webhook_digest_check CHECK (payload_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT payment_webhook_status_check CHECK (processing_status IN (
        'RECEIVED', 'PROCESSING', 'PROCESSED', 'RETRYABLE_FAILURE', 'REJECTED'
    ))
);

CREATE TABLE payment_webhook_processing_attempts (
    payment_webhook_processing_attempt_id UUID PRIMARY KEY,
    payment_webhook_event_id UUID NOT NULL
        REFERENCES payment_webhook_events(payment_webhook_event_id),
    attempt_number SMALLINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    result_code VARCHAR(64),
    failure_code VARCHAR(64),
    CONSTRAINT payment_webhook_attempt_number_check CHECK (attempt_number > 0),
    CONSTRAINT payment_webhook_attempt_unique UNIQUE (payment_webhook_event_id, attempt_number)
);

CREATE TABLE payment_reconciliation_records (
    payment_reconciliation_record_id UUID PRIMARY KEY,
    payment_order_id UUID NOT NULL REFERENCES payment_orders(payment_order_id),
    reconciliation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reason_code VARCHAR(64) NOT NULL,
    observed_provider_status VARCHAR(32),
    next_attempt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT payment_reconciliation_status_check CHECK (
        reconciliation_status IN ('PENDING', 'IN_PROGRESS', 'MATCHED', 'MISMATCH', 'FAILED')
    ),
    CONSTRAINT payment_reconciliation_provider_status_check CHECK (
        observed_provider_status IS NULL OR observed_provider_status IN (
            'CREATED', 'INITIATED', 'CHECKOUT_PENDING', 'PROCESSING', 'SUCCEEDED',
            'FAILED', 'CANCELLED', 'EXPIRED', 'REQUIRES_RECONCILIATION', 'REVERSED'
        )
    )
);

CREATE INDEX payment_reconciliation_pending_idx
    ON payment_reconciliation_records(next_attempt_at, payment_reconciliation_record_id)
    WHERE reconciliation_status IN ('PENDING', 'IN_PROGRESS', 'FAILED');

CREATE TABLE payment_financial_exceptions (
    payment_financial_exception_id UUID PRIMARY KEY,
    payment_order_id UUID NOT NULL,
    booking_id UUID NOT NULL,
    exception_code VARCHAR(64) NOT NULL,
    exception_status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT payment_financial_exception_status_check
        CHECK (exception_status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED')),
    CONSTRAINT payment_financial_exception_details_object_check
        CHECK (jsonb_typeof(details) = 'object'),
    CONSTRAINT payment_exception_order_booking_fk
        FOREIGN KEY (payment_order_id, booking_id)
        REFERENCES payment_orders(payment_order_id, booking_id)
);

CREATE INDEX payment_financial_exceptions_open_idx
    ON payment_financial_exceptions(created_at, payment_financial_exception_id)
    WHERE exception_status <> 'RESOLVED';
CREATE UNIQUE INDEX payment_financial_exception_order_code_open_unique
    ON payment_financial_exceptions(payment_order_id, exception_code)
    WHERE exception_status <> 'RESOLVED';

CREATE TABLE payment_audit_records (
    payment_audit_record_id UUID PRIMARY KEY,
    payment_order_id UUID REFERENCES payment_orders(payment_order_id),
    actor_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(64) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT payment_audit_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX payment_audit_order_idx
    ON payment_audit_records(payment_order_id, occurred_at DESC);

CREATE FUNCTION prevent_payment_history_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Payment history and audit records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_status_history_append_only
    BEFORE UPDATE OR DELETE ON payment_status_history
    FOR EACH ROW EXECUTE FUNCTION prevent_payment_history_mutation();

CREATE TRIGGER payment_audit_records_append_only
    BEFORE UPDATE OR DELETE ON payment_audit_records
    FOR EACH ROW EXECUTE FUNCTION prevent_payment_history_mutation();

CREATE FUNCTION guard_payment_idempotency_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payment idempotency records cannot be deleted';
    END IF;
    IF NEW.payment_idempotency_record_id <> OLD.payment_idempotency_record_id
       OR NEW.owner_user_id <> OLD.owner_user_id
       OR NEW.idempotency_key <> OLD.idempotency_key
       OR NEW.request_fingerprint <> OLD.request_fingerprint
       OR NEW.created_at <> OLD.created_at
       OR (OLD.payment_order_id IS NOT NULL
           AND NEW.payment_order_id IS DISTINCT FROM OLD.payment_order_id)
       OR (OLD.payment_order_id IS NULL AND NEW.payment_order_id IS NULL) THEN
        RAISE EXCEPTION 'Payment idempotency record is immutable after completion';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_idempotency_immutable
    BEFORE UPDATE OR DELETE ON payment_idempotency_records
    FOR EACH ROW EXECUTE FUNCTION guard_payment_idempotency_mutation();

CREATE FUNCTION guard_payment_order_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payment orders cannot be deleted';
    END IF;
    IF NEW.payment_order_id <> OLD.payment_order_id
       OR NEW.booking_id <> OLD.booking_id
       OR NEW.owner_user_id <> OLD.owner_user_id
       OR NEW.provider_code <> OLD.provider_code
       OR NEW.merchant_reference <> OLD.merchant_reference
       OR (OLD.provider_payment_intent_id IS NOT NULL
           AND NEW.provider_payment_intent_id IS DISTINCT FROM OLD.provider_payment_intent_id)
       OR (OLD.provider_session_id IS NOT NULL
           AND NEW.provider_session_id IS DISTINCT FROM OLD.provider_session_id)
       OR (OLD.checkout_url IS NOT NULL AND NEW.checkout_url IS DISTINCT FROM OLD.checkout_url)
       OR NEW.amount_minor <> OLD.amount_minor
       OR NEW.currency <> OLD.currency
       OR NEW.booking_amount_snapshot <> OLD.booking_amount_snapshot
       OR NEW.fare_quote_id <> OLD.fare_quote_id
       OR NEW.expires_at <> OLD.expires_at
       OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'Payment order identity and amount snapshot are immutable';
    END IF;
    IF NEW.payment_status <> OLD.payment_status AND NOT (
        (OLD.payment_status = 'CREATED'
            AND NEW.payment_status IN ('INITIATED', 'CANCELLED', 'EXPIRED'))
        OR (OLD.payment_status = 'INITIATED'
            AND NEW.payment_status IN ('CHECKOUT_PENDING', 'PROCESSING', 'FAILED',
                                       'CANCELLED', 'EXPIRED', 'REQUIRES_RECONCILIATION'))
        OR (OLD.payment_status = 'CHECKOUT_PENDING'
            AND NEW.payment_status IN ('PROCESSING', 'SUCCEEDED', 'FAILED',
                                       'CANCELLED', 'EXPIRED', 'REQUIRES_RECONCILIATION'))
        OR (OLD.payment_status = 'PROCESSING'
            AND NEW.payment_status IN ('SUCCEEDED', 'FAILED', 'CANCELLED',
                                       'EXPIRED', 'REQUIRES_RECONCILIATION'))
        OR (OLD.payment_status = 'REQUIRES_RECONCILIATION'
            AND NEW.payment_status IN ('CHECKOUT_PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED',
                                       'CANCELLED', 'EXPIRED'))
        OR (OLD.payment_status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REVERSED')
            AND NEW.payment_status = 'REQUIRES_RECONCILIATION')
    ) THEN
        RAISE EXCEPTION 'Invalid payment state transition: % -> %',
            OLD.payment_status, NEW.payment_status;
    END IF;
    IF NEW.payment_status = 'SUCCEEDED' AND OLD.payment_status <> 'SUCCEEDED'
       AND NOT EXISTS (
           SELECT 1
           FROM payment_status_history history
           WHERE history.payment_order_id = NEW.payment_order_id
             AND history.previous_status = OLD.payment_status
             AND history.new_status = 'SUCCEEDED'
             AND history.source IN ('STATUS_QUERY', 'RECONCILIATION')
       ) THEN
        RAISE EXCEPTION 'Payment success requires verified provider evidence';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_orders_state_guard
    BEFORE UPDATE OR DELETE ON payment_orders
    FOR EACH ROW EXECUTE FUNCTION guard_payment_order_mutation();

INSERT INTO permissions(permission_code, description) VALUES
    ('payments:own:create', 'Create demo payment orders for own bookings'),
    ('payments:own:read', 'Read own payment order status'),
    ('payments:admin:read', 'Read demo payment order audit details')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code)
SELECT 'PASSENGER', permission_code
FROM permissions
WHERE permission_code IN ('payments:own:create', 'payments:own:read')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code)
SELECT roles.role_code, permissions.permission_code
FROM (VALUES ('FINANCE_ADMIN'), ('SUPER_ADMIN')) roles(role_code)
CROSS JOIN permissions
WHERE permissions.permission_code = 'payments:admin:read'
ON CONFLICT DO NOTHING;



-- ==========================================================================
-- Migration: V13__confirm_bookings_after_verified_payment.sql
-- ==========================================================================

ALTER TABLE bookings
    DROP CONSTRAINT bookings_status_check;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
        CHECK (status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'EXPIRED'));

ALTER TABLE booking_status_history
    DROP CONSTRAINT booking_status_history_status_check;

ALTER TABLE booking_status_history
    ADD CONSTRAINT booking_status_history_status_check CHECK (
        (previous_status IS NULL OR previous_status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'EXPIRED'))
        AND new_status IN ('AWAITING_PAYMENT', 'CONFIRMED', 'EXPIRED')
    );

ALTER TABLE booking_audit_log
    DROP CONSTRAINT booking_audit_event_type_check;

ALTER TABLE booking_audit_log
    ADD CONSTRAINT booking_audit_event_type_check CHECK (
        event_type IN (
            'BOOKING_CREATED', 'BOOKING_EXPIRED', 'BOOKING_PAYMENT_CONFIRMED',
            'ALLOCATION_RELEASE_RETRY'
        )
    );

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
           OLD.status = 'AWAITING_PAYMENT'
           AND NEW.status IN ('EXPIRED', 'CONFIRMED')
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
             AND history.previous_status IS NOT NULL
             AND history.new_status = 'SUCCEEDED'
             AND history.source = 'STATUS_QUERY'
       ) THEN
        RAISE EXCEPTION 'A booking can only be confirmed after verified payment';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;



-- ==========================================================================
-- Migration: V14__create_ticket_management.sql
-- ==========================================================================

CREATE TABLE tickets (
    ticket_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL REFERENCES users(user_id),
    pnr VARCHAR(24) NOT NULL UNIQUE,
    ticket_status VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tickets_booking_owner_fk
        FOREIGN KEY (booking_id, owner_user_id) REFERENCES bookings(booking_id, owner_user_id),
    CONSTRAINT tickets_id_booking_unique UNIQUE (ticket_id, booking_id),
    CONSTRAINT tickets_pnr_format_check CHECK (pnr ~ '^RC-TK-[A-Z2-7]{16}$'),
    CONSTRAINT tickets_status_check CHECK (ticket_status IN ('ISSUED'))
);

CREATE INDEX tickets_owner_history_idx ON tickets(owner_user_id, issued_at DESC, ticket_id);

CREATE TABLE ticket_passengers (
    ticket_passenger_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(ticket_id),
    booking_passenger_id UUID NOT NULL REFERENCES booking_passengers(booking_passenger_id),
    public_ticket_reference VARCHAR(24) NOT NULL UNIQUE,
    passenger_number SMALLINT NOT NULL,
    passenger_name VARCHAR(120) NOT NULL,
    coach_number VARCHAR(20) NOT NULL,
    berth_number VARCHAR(20) NOT NULL,
    berth_type VARCHAR(24) NOT NULL,
    fare_paid NUMERIC(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ticket_passenger_source_unique UNIQUE (booking_passenger_id),
    CONSTRAINT ticket_passenger_number_unique UNIQUE (ticket_id, passenger_number),
    CONSTRAINT ticket_passenger_fare_check CHECK (fare_paid >= 0),
    CONSTRAINT ticket_passenger_currency_check CHECK (currency = 'INR'),
    CONSTRAINT ticket_passenger_reference_check CHECK (
        public_ticket_reference ~ '^RC-PAX-[A-Za-z0-9_-]{16}$'
    )
);

CREATE INDEX ticket_passengers_ticket_idx ON ticket_passengers(ticket_id, passenger_number);

CREATE TABLE ticket_verification_tokens (
    ticket_verification_token_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(ticket_id),
    public_handle VARCHAR(32) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ticket_token_handle_check CHECK (public_handle ~ '^[A-Za-z0-9_-]{22}$'),
    CONSTRAINT ticket_token_expiry_check CHECK (expires_at > created_at)
);

CREATE INDEX ticket_verification_token_ticket_idx
    ON ticket_verification_tokens(ticket_id, expires_at DESC);

CREATE TABLE ticket_verification_rate_limits (
    requester_hash CHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ticket_verification_rate_count_check CHECK (request_count >= 0)
);

CREATE TABLE ticket_issuance_jobs (
    ticket_issuance_job_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(booking_id),
    job_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ticket_job_status_check CHECK (job_status IN ('PENDING', 'RETRY', 'COMPLETED', 'FAILED')),
    CONSTRAINT ticket_job_attempts_check CHECK (attempt_count BETWEEN 0 AND 10)
);

CREATE INDEX ticket_issuance_jobs_retry_idx
    ON ticket_issuance_jobs(next_attempt_at, created_at)
    WHERE job_status IN ('PENDING', 'RETRY');

CREATE TABLE ticket_issue_history (
    ticket_issue_history_id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(booking_id),
    ticket_id UUID,
    event_type VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT ticket_issue_history_ticket_booking_fk
        FOREIGN KEY (ticket_id, booking_id) REFERENCES tickets(ticket_id, booking_id),
    CONSTRAINT ticket_issue_history_event_check CHECK (
        event_type IN ('QUEUED', 'ISSUED', 'ISSUE_RETRY', 'ISSUE_FAILED', 'PDF_GENERATED', 'PDF_FAILED')
    )
);

CREATE INDEX ticket_issue_history_booking_idx
    ON ticket_issue_history(booking_id, occurred_at, ticket_issue_history_id);

CREATE TABLE ticket_audit_logs (
    ticket_audit_log_id UUID PRIMARY KEY,
    ticket_id UUID REFERENCES tickets(ticket_id),
    actor_user_id UUID REFERENCES users(user_id),
    action VARCHAR(32) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(64) NOT NULL,
    CONSTRAINT ticket_audit_action_check CHECK (
        action IN ('TICKET_ISSUED', 'PASSENGER_READ', 'ADMIN_LOOKUP', 'ADMIN_RETRY',
                   'OPERATIONS_VERIFY', 'PUBLIC_VERIFY', 'PDF_DOWNLOAD')
    ),
    CONSTRAINT ticket_audit_details_object_check CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX ticket_audit_ticket_idx ON ticket_audit_logs(ticket_id, occurred_at DESC);
CREATE INDEX ticket_audit_actor_idx ON ticket_audit_logs(actor_user_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION prevent_ticket_identity_mutation() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Ticket records cannot be deleted';
    END IF;
    IF NEW.ticket_id <> OLD.ticket_id
       OR NEW.booking_id <> OLD.booking_id
       OR NEW.owner_user_id <> OLD.owner_user_id
       OR NEW.pnr <> OLD.pnr
       OR NEW.issued_at <> OLD.issued_at THEN
        RAISE EXCEPTION 'Ticket identity and PNR are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tickets_identity_immutable
    BEFORE UPDATE OR DELETE ON tickets
    FOR EACH ROW EXECUTE FUNCTION prevent_ticket_identity_mutation();

CREATE OR REPLACE FUNCTION prevent_ticket_issue_history_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ticket issue history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ticket_issue_history_immutable
    BEFORE UPDATE OR DELETE ON ticket_issue_history
    FOR EACH ROW EXECUTE FUNCTION prevent_ticket_issue_history_mutation();

CREATE TRIGGER ticket_passengers_immutable
    BEFORE UPDATE OR DELETE ON ticket_passengers
    FOR EACH ROW EXECUTE FUNCTION prevent_ticket_issue_history_mutation();

CREATE TRIGGER ticket_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON ticket_audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_ticket_issue_history_mutation();

INSERT INTO permissions(permission_code, description) VALUES
    ('tickets:own:read', 'Read own RailConnect tickets and download own e-tickets'),
    ('tickets:own:verify', 'Verify own ticket records'),
    ('tickets:admin:read', 'Search RailConnect tickets for authorized administration'),
    ('tickets:admin:retry', 'Retry a previously queued ticket issuance job'),
    ('tickets:operations:verify', 'Verify RailConnect tickets for operational purposes'),
    ('tickets:operations:read', 'Read limited journey and allocation details for ticket checks')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code)
SELECT 'PASSENGER', permission_code
FROM permissions
WHERE permission_code IN ('tickets:own:read', 'tickets:own:verify')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code)
SELECT roles.role_code, permissions.permission_code
FROM (VALUES ('RAILWAY_ADMIN'), ('RESERVATION_OFFICER'), ('SUPER_ADMIN')) roles(role_code)
CROSS JOIN permissions
WHERE permissions.permission_code = 'tickets:admin:read'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code)
SELECT roles.role_code, permissions.permission_code
FROM (VALUES ('RAILWAY_ADMIN'), ('SUPER_ADMIN')) roles(role_code)
CROSS JOIN permissions
WHERE permissions.permission_code = 'tickets:admin:retry'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions(role_code, permission_code)
SELECT roles.role_code, permissions.permission_code
FROM (VALUES ('OPERATIONS_MANAGER'), ('RAILWAY_ADMIN'), ('RESERVATION_OFFICER'), ('SUPER_ADMIN')) roles(role_code)
CROSS JOIN permissions
WHERE permissions.permission_code IN ('tickets:operations:verify', 'tickets:operations:read')
ON CONFLICT DO NOTHING;




-- ==========================================================================
-- Migration: V15__create_cancellation_and_refund_management.sql
-- ==========================================================================

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
-- ============================================================================
-- RailConnect Phase 13 Database Migration: V17__create_railway_operations_control_centre.sql
-- Description: Complete Railway Operations & Control Centre with Station-based
--              tracking, dynamic delay propagation, platform management,
--              disruptions, incidents, announcements, and passenger impact assessment.
-- ============================================================================

-- 1. Station Platforms Master
CREATE TABLE station_platforms (
    platform_id UUID PRIMARY KEY,
    station_id UUID NOT NULL REFERENCES stations(station_id) ON DELETE CASCADE,
    platform_number VARCHAR(16) NOT NULL,
    max_train_length_coaches INT NOT NULL DEFAULT 24,
    is_electrified BOOLEAN NOT NULL DEFAULT TRUE,
    has_disabled_access BOOLEAN NOT NULL DEFAULT TRUE,
    operational_status VARCHAR(24) NOT NULL DEFAULT 'OPERATIONAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT station_platforms_status_check CHECK (
        operational_status IN ('OPERATIONAL', 'MAINTENANCE', 'BLOCKED', 'DECOMMISSIONED')
    ),
    CONSTRAINT station_platforms_unique_number UNIQUE (station_id, platform_number)
);

CREATE INDEX station_platforms_station_idx ON station_platforms(station_id, operational_status);

-- 2. Train Run Operational State & Real-Time Tracking State
CREATE TABLE train_run_operations (
    train_run_id UUID PRIMARY KEY REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    current_status VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED',
    current_station_id UUID REFERENCES stations(station_id),
    next_station_id UUID REFERENCES stations(station_id),
    current_platform_number VARCHAR(16),
    departure_delay_minutes INT NOT NULL DEFAULT 0,
    arrival_delay_minutes INT NOT NULL DEFAULT 0,
    completed_stops_count INT NOT NULL DEFAULT 0,
    total_stops_count INT NOT NULL DEFAULT 0,
    route_progress_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00,
    last_reported_update_at TIMESTAMPTZ,
    last_reported_by UUID REFERENCES users(user_id),
    operational_remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT train_run_operations_status_check CHECK (
        current_status IN (
            'SCHEDULED', 'PREPARATION', 'READY', 'RUNNING',
            'DELAYED', 'HALTED', 'DIVERTED', 'CANCELLED', 'COMPLETED'
        )
    ),
    CONSTRAINT train_run_operations_progress_check CHECK (
        route_progress_percent >= 0.00 AND route_progress_percent <= 100.00
    )
);

CREATE INDEX train_run_operations_status_idx ON train_run_operations(current_status, updated_at DESC);

CREATE TABLE train_run_status_history (
    history_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    actor_user_id UUID REFERENCES users(user_id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX train_run_status_history_run_idx ON train_run_status_history(train_run_id, occurred_at DESC);

-- 3. Station Operational Updates (Station-based ground tracking)
CREATE TABLE station_operational_updates (
    update_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    station_id UUID NOT NULL REFERENCES stations(station_id),
    update_type VARCHAR(24) NOT NULL, -- 'ARRIVAL', 'DEPARTURE', 'PASSING', 'HALT'
    scheduled_time TIMESTAMPTZ NOT NULL,
    actual_reported_time TIMESTAMPTZ NOT NULL,
    delay_minutes INT NOT NULL DEFAULT 0,
    platform_number VARCHAR(16),
    halt_duration_minutes INT,
    operational_remarks TEXT,
    reported_by UUID NOT NULL REFERENCES users(user_id),
    reported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT station_update_type_check CHECK (update_type IN ('ARRIVAL', 'DEPARTURE', 'PASSING', 'HALT'))
);

CREATE INDEX station_operational_updates_run_idx ON station_operational_updates(train_run_id, reported_at DESC);
CREATE INDEX station_operational_updates_station_idx ON station_operational_updates(station_id, reported_at DESC);

-- 4. Platform Assignments & Concurrency Conflict Prevention
CREATE TABLE platform_assignments (
    assignment_id UUID PRIMARY KEY,
    station_id UUID NOT NULL REFERENCES stations(station_id) ON DELETE CASCADE,
    platform_id UUID NOT NULL REFERENCES station_platforms(platform_id) ON DELETE CASCADE,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    planned_arrival TIMESTAMPTZ NOT NULL,
    planned_departure TIMESTAMPTZ NOT NULL,
    actual_arrival TIMESTAMPTZ,
    actual_departure TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    assigned_by UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT platform_assignment_status_check CHECK (
        status IN ('PLANNED', 'OCCUPIED', 'CLEARED', 'CANCELLED', 'DIVERTED')
    ),
    CONSTRAINT platform_assignment_time_window_check CHECK (planned_departure >= planned_arrival)
);

CREATE INDEX platform_assignments_active_window_idx
    ON platform_assignments(station_id, platform_id, planned_arrival, planned_departure);

-- 5. Operational Disruptions & Passenger Impact
CREATE TABLE operational_disruptions (
    disruption_id UUID PRIMARY KEY,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id) ON DELETE CASCADE,
    disruption_type VARCHAR(32) NOT NULL, -- 'DELAY', 'TEMPORARY_HALT', 'DIVERSION', 'STATION_SKIPPING', 'PLATFORM_SHIFT', 'CANCELLATION'
    affected_station_id UUID REFERENCES stations(station_id),
    start_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expected_duration_minutes INT,
    reason TEXT NOT NULL,
    operational_impact TEXT NOT NULL,
    resolution_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    reported_by UUID NOT NULL REFERENCES users(user_id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT disruption_type_check CHECK (
        disruption_type IN ('DELAY', 'TEMPORARY_HALT', 'DIVERSION', 'STATION_SKIPPING', 'PLATFORM_SHIFT', 'CANCELLATION')
    ),
    CONSTRAINT disruption_resolution_status_check CHECK (
        resolution_status IN ('ACTIVE', 'MONITORING', 'RESOLVED', 'SUPERSEDED')
    )
);

CREATE INDEX operational_disruptions_run_idx ON operational_disruptions(train_run_id, resolution_status);

CREATE TABLE passenger_impact_assessments (
    assessment_id UUID PRIMARY KEY,
    disruption_id UUID NOT NULL REFERENCES operational_disruptions(disruption_id) ON DELETE CASCADE,
    train_run_id UUID NOT NULL REFERENCES inventory_train_runs(train_run_id),
    total_affected_passengers INT NOT NULL DEFAULT 0,
    total_affected_bookings INT NOT NULL DEFAULT 0,
    impact_level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    recommendation TEXT,
    notifications_dispatched INT NOT NULL DEFAULT 0,
    assessed_by UUID NOT NULL REFERENCES users(user_id),
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT passenger_impact_level_check CHECK (impact_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- 6. Operational Incidents & Incident History
CREATE TABLE operational_incidents (
    incident_id UUID PRIMARY KEY,
    incident_number VARCHAR(32) NOT NULL UNIQUE,
    category VARCHAR(32) NOT NULL, -- 'BREAKDOWN', 'TECHNICAL_ISSUE', 'STATION_ISSUE', 'SAFETY', 'ROUTE_DISRUPTION', 'PLATFORM_CONFLICT', 'COMMUNICATION'
    priority VARCHAR(16) NOT NULL DEFAULT 'P3', -- 'P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW'
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    train_run_id UUID REFERENCES inventory_train_runs(train_run_id),
    station_id UUID REFERENCES stations(station_id),
    assigned_to UUID REFERENCES users(user_id),
    reported_by UUID NOT NULL REFERENCES users(user_id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT incident_status_check CHECK (
        status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'ESCALATED', 'RESOLVED', 'CLOSED')
    ),
    CONSTRAINT incident_priority_check CHECK (
        priority IN ('P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW')
    )
);

CREATE INDEX operational_incidents_status_priority_idx ON operational_incidents(status, priority, created_at DESC);

CREATE TABLE operational_incident_history (
    incident_history_id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES operational_incidents(incident_id) ON DELETE CASCADE,
    previous_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    remarks TEXT NOT NULL,
    action_by UUID NOT NULL REFERENCES users(user_id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX operational_incident_history_incident_idx ON operational_incident_history(incident_id, occurred_at DESC);

-- 7. Operational Announcements & Bulletins
CREATE TABLE operational_announcements (
    announcement_id UUID PRIMARY KEY,
    announcement_type VARCHAR(32) NOT NULL, -- 'TRAIN_DELAY', 'PLATFORM_CHANGE', 'ARRIVAL_DEPARTURE', 'DIVERSION', 'CANCELLATION', 'STATION_NOTICE', 'GENERAL'
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    train_run_id UUID REFERENCES inventory_train_runs(train_run_id),
    station_id UUID REFERENCES stations(station_id),
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    start_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expiry_time TIMESTAMPTZ,
    published_by UUID NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT announcement_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'EXPIRED', 'ARCHIVED')),
    CONSTRAINT announcement_priority_check CHECK (priority IN ('URGENT', 'HIGH', 'NORMAL', 'LOW'))
);

CREATE INDEX operational_announcements_active_idx
    ON operational_announcements(status, start_time, expiry_time);

-- 8. Audit Logs
CREATE TABLE operational_control_audit_logs (
    audit_id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(user_id),
    event_type VARCHAR(48) NOT NULL,
    target_type VARCHAR(48) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    details_json TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX operational_control_audit_idx ON operational_control_audit_logs(occurred_at DESC);

-- 9. RBAC Permissions for Operations & Control Centre
INSERT INTO permissions (permission_code, description) VALUES
('operations:control', 'Full access to Control Centre dashboard and operational management'),
('operations:station:update', 'Record station arrival, departure, and passing updates'),
('operations:platform:manage', 'Assign, modify, and monitor station platforms'),
('operations:incident:manage', 'Create, update, escalate, and resolve operational incidents'),
('operations:announcement:publish', 'Publish public station and train operational bulletins'),
('operations:tracking:read', 'Inspect train operational progress, route status, and delay telemetry')
ON CONFLICT (permission_code) DO NOTHING;

-- Grant permissions to Operations and Railway roles
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'OPERATIONS_MANAGER', p.permission_code
FROM permissions p
WHERE p.permission_code IN (
    'operations:control', 'operations:station:update', 'operations:platform:manage',
    'operations:incident:manage', 'operations:announcement:publish', 'operations:tracking:read'
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', p.permission_code
FROM permissions p
WHERE p.permission_code IN (
    'operations:control', 'operations:station:update', 'operations:platform:manage',
    'operations:incident:manage', 'operations:announcement:publish', 'operations:tracking:read'
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'RAILWAY_ADMIN', p.permission_code
FROM permissions p
WHERE p.permission_code IN (
    'operations:control', 'operations:station:update', 'operations:platform:manage',
    'operations:incident:manage', 'operations:announcement:publish', 'operations:tracking:read'
)
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'STATION_MANAGER', p.permission_code
FROM permissions p
WHERE p.permission_code IN (
    'operations:station:update', 'operations:platform:manage',
    'operations:incident:manage', 'operations:announcement:publish', 'operations:tracking:read'
)
ON CONFLICT DO NOTHING;
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
-- V18: Passenger Communication and Notification System
-- Implements centralized notification schema: templates, preferences, devices, outbox, in-app inbox, deliveries, announcements

CREATE TABLE IF NOT EXISTS notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code VARCHAR(80) NOT NULL UNIQUE,
    category VARCHAR(40) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    title_template VARCHAR(200) NOT NULL,
    body_template TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    default_deep_link VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id UUID PRIMARY KEY,
    in_app_enabled BOOLEAN NOT NULL DEFAULT true,
    push_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled BOOLEAN NOT NULL DEFAULT false,
    booking_alerts BOOLEAN NOT NULL DEFAULT true,
    payment_alerts BOOLEAN NOT NULL DEFAULT true,
    ticket_alerts BOOLEAN NOT NULL DEFAULT true,
    reservation_alerts BOOLEAN NOT NULL DEFAULT true,
    operational_alerts BOOLEAN NOT NULL DEFAULT true,
    announcement_alerts BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_token TEXT NOT NULL,
    platform VARCHAR(30) NOT NULL DEFAULT 'WEB',
    device_metadata JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notification_device UNIQUE (user_id, device_token)
);

CREATE TABLE IF NOT EXISTS notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    user_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    category VARCHAR(40) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    is_read BOOLEAN NOT NULL DEFAULT false,
    read_at TIMESTAMPTZ,
    deep_link VARCHAR(255),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS notification_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    channel VARCHAR(30) NOT NULL,
    provider VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 1,
    provider_response JSONB DEFAULT '{}'::jsonb,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notification_announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    category VARCHAR(40) NOT NULL DEFAULT 'ANNOUNCEMENT',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    target_scope VARCHAR(30) NOT NULL DEFAULT 'ALL',
    target_train_run_id UUID,
    target_station_code VARCHAR(10),
    is_active BOOLEAN NOT NULL DEFAULT true,
    published_by VARCHAR(120) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ
);

-- Indexes for performance and scale
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_category ON notifications(category);
CREATE INDEX IF NOT EXISTS idx_notification_outbox_pending ON notification_outbox(status, next_retry_at) WHERE status IN ('PENDING', 'RETRYING');
CREATE INDEX IF NOT EXISTS idx_notification_devices_user ON notification_devices(user_id, is_active);
CREATE INDEX IF NOT EXISTS idx_notification_deliveries_notif ON notification_deliveries(notification_id);

-- Seed Default Notification Templates
INSERT INTO notification_templates (template_code, category, event_type, title_template, body_template, priority, default_deep_link)
VALUES
('BOOKING_CONFIRMED', 'BOOKING', 'BOOKING_CONFIRMED', 'Booking Confirmed: {bookingReference}', 'Your booking {bookingReference} on Train {trainNumber} ({travelDate}) has been confirmed.', 'HIGH', '/tickets'),
('PAYMENT_SUCCESS', 'PAYMENT', 'PAYMENT_SUCCESS', 'Payment Verified: {paymentId}', 'Your payment of INR {amount} has been successfully verified via {paymentProvider}.', 'HIGH', '/tickets'),
('PAYMENT_FAILED', 'PAYMENT', 'PAYMENT_FAILED', 'Payment Failed for Booking {bookingReference}', 'Your payment attempt was unsuccessful. Reason: {reason}. You may retry within the hold window.', 'URGENT', '/checkout'),
('TICKET_ISSUED', 'TICKET', 'TICKET_ISSUED', 'Ticket & PNR Issued: {pnr}', 'PNR {pnr} generated for your journey on {trainNumber}. Tap to view your digital ticket.', 'HIGH', '/tickets'),
('WAITLIST_PROMOTED', 'WAITLIST', 'WAITLIST_PROMOTED', 'Waitlist Status Upgraded', 'Passenger {passengerName} has been promoted from {fromStatus} to {toStatus} on PNR {pnr}.', 'HIGH', '/tickets'),
('RAC_PROMOTED', 'RAC', 'RAC_PROMOTED', 'RAC Confirmed', 'Passenger {passengerName} has been confirmed with coach {coachNumber} and berth {berthNumber} on PNR {pnr}.', 'HIGH', '/tickets'),
('CHART_PREPARED', 'RESERVATION', 'CHART_PREPARED', 'Chart Prepared for Train {trainNumber}', 'Final departure chart for train {trainNumber} is now prepared. Please check final berth allocations.', 'HIGH', '/tickets'),
('CANCELLATION_CONFIRMED', 'CANCELLATION', 'CANCELLATION_CONFIRMED', 'Booking Cancelled: {bookingReference}', 'Your booking cancellation has been confirmed. Calculated refund amount: INR {refundAmount}.', 'HIGH', '/cancellations'),
('REFUND_COMPLETED', 'REFUND', 'REFUND_COMPLETED', 'Refund Credited: INR {refundAmount}', 'Your refund of INR {refundAmount} has been processed and credited to your original payment source.', 'HIGH', '/cancellations'),
('TRAIN_DELAYED', 'TRAIN_OPERATION', 'TRAIN_DELAYED', 'Train {trainNumber} Running Delay', 'Train {trainNumber} is currently running with an estimated delay of {delayMinutes} minutes at {currentStation}.', 'URGENT', '/'),
('PLATFORM_CHANGED', 'PLATFORM_CHANGE', 'PLATFORM_CHANGED', 'Platform Changed at {stationCode}', 'Train {trainNumber} will now arrive on Platform {platformNumber} at {stationCode}.', 'URGENT', '/'),
('OPERATIONAL_ANNOUNCEMENT', 'ANNOUNCEMENT', 'OPERATIONAL_ANNOUNCEMENT', '{announcementTitle}', '{announcementBody}', 'NORMAL', '/')
ON CONFLICT (template_code) DO NOTHING;
