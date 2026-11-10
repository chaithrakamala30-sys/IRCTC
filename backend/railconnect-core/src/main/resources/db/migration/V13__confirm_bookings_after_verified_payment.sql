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
