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

INSERT INTO roles (role_code, display_name) VALUES
    ('PASSENGER', 'Passenger'),
    ('SUPER_ADMIN', 'Super Administrator'),
    ('RAILWAY_ADMIN', 'Railway Administrator'),
    ('STATION_MANAGER', 'Station Manager'),
    ('RESERVATION_OFFICER', 'Reservation Officer'),
    ('TICKET_CHECKER', 'Ticket Checking Staff')
ON CONFLICT (role_code) DO NOTHING;

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
