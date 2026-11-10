-- ============================================================================
-- RailConnect Phase 15 Database Migration: V19__create_customer_support_and_complaint_management.sql
-- Description: Customer Support, Complaint Lifecycle, Internal Staff Notes,
--              SLA Tracking, Escalations, and Passenger Satisfaction Feedback.
-- ============================================================================

-- 1. Support Categories
CREATE TABLE IF NOT EXISTS support_categories (
    category_id UUID PRIMARY KEY,
    category_code VARCHAR(40) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    department VARCHAR(40) NOT NULL DEFAULT 'GENERAL',
    default_priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    sla_response_hours INT NOT NULL DEFAULT 4,
    sla_resolution_hours INT NOT NULL DEFAULT 24,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Support Teams
CREATE TABLE IF NOT EXISTS support_teams (
    team_id UUID PRIMARY KEY,
    team_code VARCHAR(40) NOT NULL UNIQUE,
    team_name VARCHAR(120) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Support Team Members
CREATE TABLE IF NOT EXISTS support_team_members (
    membership_id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES support_teams(team_id) ON DELETE CASCADE,
    staff_user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_in_team VARCHAR(30) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_staff UNIQUE (team_id, staff_user_id)
);

-- 4. Support Tickets
CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id UUID PRIMARY KEY,
    ticket_reference VARCHAR(32) NOT NULL UNIQUE,
    passenger_user_id UUID NOT NULL REFERENCES users(user_id),
    category_id UUID NOT NULL REFERENCES support_categories(category_id),
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    assigned_team_id UUID REFERENCES support_teams(team_id),
    assigned_staff_id UUID REFERENCES users(user_id),
    linked_booking_id UUID,
    linked_payment_id UUID,
    linked_ticket_id UUID,
    linked_train_run_id UUID,
    sla_target_response_at TIMESTAMPTZ,
    sla_target_resolution_at TIMESTAMPTZ,
    first_responded_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT support_ticket_status_check CHECK (
        status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER',
                   'WAITING_FOR_INTERNAL_TEAM', 'ESCALATED', 'RESOLVED', 'CLOSED', 'REOPENED')
    ),
    CONSTRAINT support_ticket_priority_check CHECK (
        priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT', 'CRITICAL')
    )
);

-- 5. Support Ticket Messages (Passenger Replies vs. Internal Staff Notes)
CREATE TABLE IF NOT EXISTS support_ticket_messages (
    message_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(ticket_id) ON DELETE CASCADE,
    sender_user_id UUID NOT NULL REFERENCES users(user_id),
    sender_type VARCHAR(20) NOT NULL DEFAULT 'PASSENGER',
    is_internal_note BOOLEAN NOT NULL DEFAULT false,
    message_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT support_message_sender_check CHECK (sender_type IN ('PASSENGER', 'STAFF', 'SYSTEM'))
);

-- 6. Support Ticket Attachments
CREATE TABLE IF NOT EXISTS support_ticket_attachments (
    attachment_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(ticket_id) ON DELETE CASCADE,
    message_id UUID REFERENCES support_ticket_messages(message_id) ON DELETE SET NULL,
    uploader_user_id UUID NOT NULL REFERENCES users(user_id),
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    storage_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Support Ticket Status History & Audits
CREATE TABLE IF NOT EXISTS support_ticket_status_history (
    history_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(ticket_id) ON DELETE CASCADE,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    changed_by_user_id UUID NOT NULL REFERENCES users(user_id),
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. Support Escalations
CREATE TABLE IF NOT EXISTS support_escalations (
    escalation_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(ticket_id) ON DELETE CASCADE,
    escalation_level INT NOT NULL DEFAULT 1,
    escalated_by_user_id UUID NOT NULL REFERENCES users(user_id),
    reason TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. Passenger Satisfaction Feedback
CREATE TABLE IF NOT EXISTS support_feedback (
    feedback_id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL UNIQUE REFERENCES support_tickets(ticket_id) ON DELETE CASCADE,
    passenger_user_id UUID NOT NULL REFERENCES users(user_id),
    satisfaction_rating INT NOT NULL CHECK (satisfaction_rating BETWEEN 1 AND 5),
    comments TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Performance and scale indexes
CREATE INDEX IF NOT EXISTS idx_support_tickets_passenger ON support_tickets(passenger_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_status ON support_tickets(status, priority);
CREATE INDEX IF NOT EXISTS idx_support_tickets_staff ON support_tickets(assigned_staff_id, status);
CREATE INDEX IF NOT EXISTS idx_support_tickets_team ON support_tickets(assigned_team_id, status);
CREATE INDEX IF NOT EXISTS idx_support_messages_ticket ON support_ticket_messages(ticket_id, created_at ASC);

-- Seed Categories
INSERT INTO support_categories (category_id, category_code, display_name, department, default_priority, sla_response_hours, sla_resolution_hours) VALUES
('22222222-2222-2222-2222-222222222001', 'PAYMENT_PENDING', 'Payment Deducted / Ticket Not Confirmed', 'FINANCE', 'URGENT', 2, 8),
('22222222-2222-2222-2222-222222222002', 'REFUND_DISPUTE', 'Refund Amount / Status Dispute', 'FINANCE', 'HIGH', 4, 24),
('22222222-2222-2222-2222-222222222003', 'BOOKING_ISSUE', 'Booking Failure / Passenger Details', 'BOOKING', 'NORMAL', 4, 24),
('22222222-2222-2222-2222-222222222004', 'TICKET_PNR_PROBLEM', 'Ticket PDF Unavailable / PNR Verification', 'TICKETING', 'HIGH', 2, 12),
('22222222-2222-2222-2222-222222222005', 'TRAIN_OPERATIONS_ISSUE', 'Train Delay / Platform Discrepancy', 'OPERATIONS', 'HIGH', 2, 12),
('22222222-2222-2222-2222-222222222006', 'GENERAL_ENQUIRY', 'General Railway Enquiry & Feedback', 'GENERAL', 'LOW', 8, 48)
ON CONFLICT (category_code) DO NOTHING;

-- Seed Default Teams
INSERT INTO support_teams (team_id, team_code, team_name, description) VALUES
('33333333-3333-3333-3333-333333333001', 'GENERAL_SUPPORT', 'General Passenger Helpdesk', 'First-line support for passenger enquiries'),
('33333333-3333-3333-3333-333333333002', 'FINANCE_SUPPORT', 'Payment & Refund Helpdesk', 'Specialized team for payment failures and refund reconciliations'),
('33333333-3333-3333-3333-333333333003', 'OPERATIONS_SUPPORT', 'Operations Escalations Helpdesk', 'Handles platform, train delays, and schedule discrepancies')
ON CONFLICT (team_code) DO NOTHING;

-- RBAC Permissions for Customer Support
INSERT INTO permissions (permission_code, description) VALUES
('support:tickets:view_all', 'View all customer support tickets across queues'),
('support:tickets:assign', 'Assign or reassign support tickets to staff or teams'),
('support:tickets:reply', 'Reply to passenger complaints as authorized support staff'),
('support:tickets:internal_notes', 'Add and view confidential internal staff notes'),
('support:tickets:resolve', 'Change ticket resolution and closure state'),
('support:tickets:escalate', 'Escalate customer support tickets to supervisors')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', p.permission_code
FROM permissions p
WHERE p.permission_code LIKE 'support:%'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_code, permission_code)
SELECT 'CUSTOMER_SUPPORT', p.permission_code
FROM permissions p
WHERE p.permission_code IN ('support:tickets:view_all', 'support:tickets:assign', 'support:tickets:reply', 'support:tickets:internal_notes', 'support:tickets:resolve', 'support:tickets:escalate')
ON CONFLICT DO NOTHING;
