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
