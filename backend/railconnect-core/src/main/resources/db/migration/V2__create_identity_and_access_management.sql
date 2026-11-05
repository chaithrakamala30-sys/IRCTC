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
