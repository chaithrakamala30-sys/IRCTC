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
