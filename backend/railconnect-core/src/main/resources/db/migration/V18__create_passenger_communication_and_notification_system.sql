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
