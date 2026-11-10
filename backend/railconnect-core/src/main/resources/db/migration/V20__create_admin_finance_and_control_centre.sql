-- ============================================================================
-- RailConnect — Phase 16: Administration, Finance & Management Control Centre
-- Migration: V20__create_admin_finance_and_control_centre.sql
-- ============================================================================

-- 1. Staff Departments & Profiles
CREATE TABLE IF NOT EXISTS staff_departments (
    department_code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO staff_departments (department_code, name, description)
VALUES
    ('ADMINISTRATION', 'Platform Administration', 'Platform management, governance, and user access oversight'),
    ('FINANCE', 'Finance & Reconciliation', 'Financial settlement, reconciliation, and refund verification'),
    ('RESERVATIONS', 'Reservation & Charting', 'Quota management, RAC, waitlist, and chart operations'),
    ('CUSTOMER_SUPPORT', 'Customer Experience', 'Passenger dispute resolution and ticket escalation triage'),
    ('OPERATIONS', 'Railway Operations', 'Train dispatch, OCC coordination, and disruption response'),
    ('SECURITY', 'Audit & Security', 'Compliance review, access verification, and incident management')
ON CONFLICT (department_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS staff_profiles (
    staff_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    staff_code VARCHAR(32) NOT NULL UNIQUE,
    department_code VARCHAR(40) NOT NULL REFERENCES staff_departments(department_code),
    designation VARCHAR(80) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_staff_profiles_department ON staff_profiles(department_code);
CREATE INDEX IF NOT EXISTS idx_staff_profiles_active ON staff_profiles(is_active);

-- 2. Maker-Checker Approval Workflow Engine
CREATE TABLE IF NOT EXISTS admin_approval_requests (
    request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
    requested_by UUID NOT NULL REFERENCES users(user_id),
    reviewed_by UUID REFERENCES users(user_id),
    review_remarks TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    applied_at TIMESTAMPTZ,
    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'APPLIED')),
    CONSTRAINT chk_no_self_approval CHECK (
        status <> 'APPROVED' OR (reviewed_by IS NOT NULL AND requested_by <> reviewed_by)
    )
);

CREATE INDEX IF NOT EXISTS idx_approval_status ON admin_approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_approval_type ON admin_approval_requests(request_type);
CREATE INDEX IF NOT EXISTS idx_approval_requested_by ON admin_approval_requests(requested_by);

-- 3. Finance & Reconciliation Centre
CREATE TABLE IF NOT EXISTS finance_reconciliation_runs (
    run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_code VARCHAR(40) NOT NULL UNIQUE,
    run_date DATE NOT NULL,
    total_transactions INTEGER NOT NULL DEFAULT 0,
    matched_count INTEGER NOT NULL DEFAULT 0,
    discrepancy_count INTEGER NOT NULL DEFAULT 0,
    total_volume_inr NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(24) NOT NULL DEFAULT 'COMPLETED',
    initiated_by UUID NOT NULL REFERENCES users(user_id),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reconciliation_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FLAGGED_DISCREPANCIES'))
);

CREATE TABLE IF NOT EXISTS finance_reconciliation_items (
    item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES finance_reconciliation_runs(run_id) ON DELETE CASCADE,
    reference_type VARCHAR(24) NOT NULL,
    internal_reference VARCHAR(64) NOT NULL,
    gateway_reference VARCHAR(64),
    amount_inr NUMERIC(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'MATCHED',
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rec_item_type CHECK (reference_type IN ('PAYMENT', 'REFUND')),
    CONSTRAINT chk_rec_item_status CHECK (status IN ('MATCHED', 'AMOUNT_MISMATCH', 'STATUS_MISMATCH', 'UNMATCHED_INTERNAL', 'UNMATCHED_GATEWAY'))
);

CREATE INDEX IF NOT EXISTS idx_rec_items_run ON finance_reconciliation_items(run_id);
CREATE INDEX IF NOT EXISTS idx_rec_items_status ON finance_reconciliation_items(status);

-- 4. Centralized System Configurations
CREATE TABLE IF NOT EXISTS system_configurations (
    config_key VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    is_protected BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by UUID REFERENCES users(user_id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_configurations (config_key, config_value, description, is_protected)
VALUES
    ('MAINTENANCE_MODE_ENABLED', 'false', 'Global maintenance mode disabling public booking portal', TRUE),
    ('PASSENGER_REGISTRATION_ENABLED', 'true', 'Controls whether new passenger accounts can self-register', FALSE),
    ('BOOKING_SERVICE_ENABLED', 'true', 'Master switch for train booking and ticket reservation engines', TRUE),
    ('AUTO_REFUND_MAX_LIMIT_INR', '10000.00', 'Maximum refund threshold for automatic Dhruva Bank refund processing without maker-checker approval', FALSE),
    ('SUPPORT_TICKET_SLA_MULTIPLIER', '1.0', 'Multiplier applied to default response and resolution SLAs', FALSE),
    ('DEMO_PAYMENT_GATEWAY_URL', 'https://dhruva-bank.vercel.app', 'Dhruva Bank demo payment gateway endpoint', TRUE)
ON CONFLICT (config_key) DO NOTHING;

-- 5. Additional Granular Permissions for Admin & Finance
INSERT INTO permissions (permission_code, description)
VALUES
    ('admin:dashboard:view', 'View executive analytics and management KPIs'),
    ('admin:users:manage', 'Search, suspend, and restore passenger accounts'),
    ('admin:staff:manage', 'Create and manage staff profiles and department assignments'),
    ('admin:roles:manage', 'Manage roles and granular permission allocations'),
    ('admin:approvals:create', 'Create maker requests for sensitive administrative changes'),
    ('admin:approvals:review', 'Review and approve/reject maker-checker requests (checker)'),
    ('admin:finance:view', 'View financial summaries, demo payment volumes, and refund totals'),
    ('admin:finance:reconcile', 'Execute and review Dhruva Bank reconciliation runs'),
    ('admin:reports:export', 'Generate and export administrative, financial, and operational CSV reports'),
    ('admin:audit:view', 'View centralized security audit logs and administrative traces'),
    ('admin:config:manage', 'Modify system settings and operational parameters')
ON CONFLICT (permission_code) DO NOTHING;

-- Assign permissions to management roles
INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('SUPER_ADMIN', 'admin:dashboard:view'),
    ('SUPER_ADMIN', 'admin:users:manage'),
    ('SUPER_ADMIN', 'admin:staff:manage'),
    ('SUPER_ADMIN', 'admin:roles:manage'),
    ('SUPER_ADMIN', 'admin:approvals:create'),
    ('SUPER_ADMIN', 'admin:approvals:review'),
    ('SUPER_ADMIN', 'admin:finance:view'),
    ('SUPER_ADMIN', 'admin:finance:reconcile'),
    ('SUPER_ADMIN', 'admin:reports:export'),
    ('SUPER_ADMIN', 'admin:audit:view'),
    ('SUPER_ADMIN', 'admin:config:manage'),

    ('RAILWAY_ADMIN', 'admin:dashboard:view'),
    ('RAILWAY_ADMIN', 'admin:users:manage'),
    ('RAILWAY_ADMIN', 'admin:approvals:create'),
    ('RAILWAY_ADMIN', 'admin:approvals:review'),
    ('RAILWAY_ADMIN', 'admin:reports:export'),
    ('RAILWAY_ADMIN', 'admin:audit:view')
ON CONFLICT DO NOTHING;

