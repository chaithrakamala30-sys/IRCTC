package in.railconnect.core.payment;

import in.railconnect.core.api.RequestIds;
import in.railconnect.core.booking.BookingService;
import in.railconnect.core.payment.PaymentModels.PaymentAdminDetails;
import in.railconnect.core.payment.PaymentModels.PaymentAdminSummary;
import in.railconnect.core.payment.PaymentModels.PaymentOrderResponse;
import in.railconnect.core.payment.PaymentProviderAdapter.CheckoutSession;
import in.railconnect.core.payment.PaymentProviderAdapter.PaymentInitiationRequest;
import in.railconnect.core.payment.PaymentProviderAdapter.VerifiedProviderStatus;
import in.railconnect.core.reservation.ReservationModels.ReservationDetails;
import in.railconnect.core.reservation.ReservationService;
import in.railconnect.core.ticket.TicketIssuanceService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static in.railconnect.core.payment.PaymentState.*;

@Service
public class PaymentOrderService {
    private static final String PROVIDER = PaymentProvider.DHRUVA_BANK.name();
    private static final long PROVIDER_EXPIRY_SAFETY_BUFFER_SECONDS = 30;
    private final JdbcTemplate jdbc;
    private final ReservationService reservationService;
    private final BookingService bookingService;
    private final TicketIssuanceService ticketIssuanceService;
    private final URI passengerOrigin;

    public PaymentOrderService(
            JdbcTemplate jdbc,
            ReservationService reservationService,
            BookingService bookingService,
            TicketIssuanceService ticketIssuanceService,
            @org.springframework.beans.factory.annotation.Value(
                    "${railconnect.pay.dhruva-bank.passenger-origin:https://irctc-rho.vercel.app}")
            String passengerOrigin
    ) {
        this.jdbc = jdbc;
        this.reservationService = reservationService;
        this.bookingService = bookingService;
        this.ticketIssuanceService = ticketIssuanceService;
        this.passengerOrigin = validatePassengerOrigin(passengerOrigin);
    }

    @Transactional
    public PreparedPayment prepare(UUID bookingId, UUID ownerId, String idempotencyKey, String experience) {
        String key = normalizeKey(idempotencyKey);
        if (!"mobile".equals(experience) && !"laptop".equals(experience)) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "PAYMENT_EXPERIENCE_INVALID",
                    "A supported passenger experience is required");
        }
        BookingForPayment booking = jdbc.query("""
                SELECT booking_id, owner_user_id, booking_reference, status, total_payable,
                       currency, fare_quote_id, reservation_request_id, payment_deadline,
                       train_snapshot ->> 'trainNumber' AS train_number,
                       train_snapshot ->> 'trainName' AS train_name,
                       journey_snapshot ->> 'coachClassCode' AS coach_class_code,
                       (SELECT COUNT(*) FROM booking_passengers p
                        WHERE p.booking_id = bookings.booking_id) AS passenger_count
                FROM bookings
                WHERE booking_id = ? AND owner_user_id = ?
                FOR UPDATE
                """, result -> result.next() ? booking(result) : null, bookingId, ownerId);
        if (booking == null) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found");
        }
        String fingerprint = sha256(bookingId + "|" + experience);
        jdbc.update("""
                INSERT INTO payment_idempotency_records(
                    payment_idempotency_record_id, owner_user_id, idempotency_key, request_fingerprint
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (owner_user_id, idempotency_key) DO NOTHING
                """, UUID.randomUUID(), ownerId, key, fingerprint);
        Idempotency row = jdbc.query("""
                SELECT request_fingerprint, payment_order_id
                FROM payment_idempotency_records
                WHERE owner_user_id = ? AND idempotency_key = ?
                FOR UPDATE
                """, result -> result.next()
                ? new Idempotency(result.getString(1), result.getObject(2, UUID.class)) : null, ownerId, key);
        if (row == null || !fingerprint.equals(row.fingerprint())) {
            throw new PaymentException(HttpStatus.CONFLICT, "PAYMENT_IDEMPOTENCY_KEY_REUSED",
                    "This idempotency key was already used for a different payment request");
        }
        PaymentRecord existing = jdbc.query("""
                SELECT payment_orders.payment_order_id, payment_orders.booking_id,
                       payment_orders.owner_user_id, bookings.booking_reference AS booking_reference,
                       payment_orders.merchant_reference,
                       payment_orders.provider_payment_intent_id, payment_orders.provider_session_id,
                       payment_orders.checkout_url, payment_orders.amount_minor, payment_orders.currency,
                       payment_orders.payment_status, payment_orders.expires_at, payment_orders.created_at,
                       payment_orders.failure_code, payment_orders.fare_quote_id,
                       bookings.train_snapshot ->> 'trainNumber' AS train_number,
                       bookings.train_snapshot ->> 'trainName' AS train_name,
                       bookings.journey_snapshot ->> 'coachClassCode' AS coach_class_code,
                       (SELECT COUNT(*) FROM booking_passengers p
                        WHERE p.booking_id = bookings.booking_id) AS passenger_count,
                       bookings.payment_deadline
                FROM payment_orders
                JOIN bookings USING (booking_id)
                WHERE payment_orders.booking_id = ? AND payment_orders.owner_user_id = ?
                FOR UPDATE OF payment_orders
                """, result -> result.next() ? payment(result) : null, bookingId, ownerId);
        if (row.paymentId() != null) {
            if (existing == null || !existing.paymentId().equals(row.paymentId())) {
                throw new PaymentException(HttpStatus.CONFLICT, "PAYMENT_IDEMPOTENCY_CONFLICT",
                        "The payment request could not be safely replayed");
            }
            ensureFreshProviderAttemptAllowed(existing, booking);
            return prepared(existing, booking, ownerId, experience);
        }
        if (existing != null) {
            ensureFreshProviderAttemptAllowed(existing, booking);
            return prepared(existing, booking, ownerId, experience);
        }
        if (!"AWAITING_PAYMENT".equals(booking.bookingStatus())
                || !booking.paymentDeadline().isAfter(databaseNow())) {
            throw new PaymentException(HttpStatus.CONFLICT, "BOOKING_NOT_PAYABLE",
                    "This booking is no longer awaiting payment");
        }
        ReservationDetails allocation = reservationService.readForBooking(booking.reservationRequestId());
        if (!"ALLOCATED_PENDING_BOOKING".equals(allocation.status())
                || allocation.expiresAt() == null
                || !allocation.expiresAt().isAfter(databaseNow())) {
            throw new PaymentException(HttpStatus.CONFLICT, "ALLOCATION_NOT_PAYABLE",
                    "A live provisional allocation is required to start payment");
        }
        Integer providerEnabled = jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment_provider_configurations
                WHERE provider_code = ? AND enabled = TRUE AND demo_mode = TRUE
                  AND contract_status = 'VERIFIED'
                """, Integer.class, PROVIDER);
        if (providerEnabled == null || providerEnabled != 1) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                    "Dhruva Bank demo payments are not enabled");
        }
        long amountMinor = PaymentAmount.toMinorUnits(booking.amount(), booking.currency());
        UUID paymentId = UUID.randomUUID();
        String merchantReference = "RC-PAY-" + booking.bookingReference();
        Instant createdAt = databaseNow();
        if (amountMinor <= 0 || !booking.paymentDeadline().isAfter(createdAt)) {
            throw new PaymentException(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_OR_DEADLINE_INVALID",
                    "This booking cannot be submitted for payment");
        }
        jdbc.update("""
                INSERT INTO payment_orders(
                    payment_order_id, booking_id, owner_user_id, provider_code, merchant_reference,
                    amount_minor, currency, booking_amount_snapshot, fare_quote_id,
                    payment_status, expires_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'CREATED', ?, ?)
                """, paymentId, bookingId, ownerId, PROVIDER, merchantReference, amountMinor,
                booking.currency(), booking.amount(), booking.fareQuoteId(),
                Timestamp.from(booking.paymentDeadline()), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO payment_status_history(
                    payment_status_history_id, payment_order_id, previous_status, new_status,
                    source, reason_code, correlation_id
                ) VALUES (?, ?, NULL, 'CREATED', 'SYSTEM', 'PAYMENT_ORDER_CREATED', ?)
                """, UUID.randomUUID(), paymentId, correlationId());
        String providerIdempotencyKey = "railconnect-" + paymentId;
        jdbc.update("""
                INSERT INTO payment_attempts(
                    payment_attempt_id, payment_order_id, attempt_number, provider_code,
                    provider_idempotency_key, attempt_status
                ) VALUES (?, ?, 1, ?, ?, 'CREATED')
                """, UUID.randomUUID(), paymentId, PROVIDER, providerIdempotencyKey);
        jdbc.update("""
                UPDATE payment_idempotency_records SET payment_order_id = ?
                WHERE owner_user_id = ? AND idempotency_key = ? AND payment_order_id IS NULL
                """, paymentId, ownerId, key);
        PaymentRecord created = new PaymentRecord(
                paymentId, bookingId, ownerId, booking.bookingReference(), null, null, null,
                amountMinor, booking.currency(), "CREATED", booking.paymentDeadline(), createdAt,
                null, booking.fareQuoteId(), booking.trainNumber(), booking.trainName(),
                booking.coachClassCode(), booking.passengerCount(), booking.paymentDeadline());
        return prepared(created, booking, ownerId, experience);
    }

    @Transactional
    public void markInitiationStarted(UUID paymentId) {
        PaymentRecord payment = lockPayment(paymentId);
        if (!List.of("CREATED", "INITIATED", "REQUIRES_RECONCILIATION").contains(payment.status())
                || payment.providerPaymentIntentId() != null) {
            return;
        }
        if ("CREATED".equals(payment.status())) {
            transition(payment, INITIATED, PaymentTransitionSource.SYSTEM, "PROVIDER_INTENT_REQUESTED");
            jdbc.update("""
                    UPDATE payment_orders
                    SET payment_status = 'INITIATED', updated_at = CURRENT_TIMESTAMP
                    WHERE payment_order_id = ?
                    """, paymentId);
        }
        jdbc.update("""
                UPDATE payment_attempts
                SET attempt_status = 'INITIATED', retry_count = retry_count + 1,
                    last_attempted_at = CURRENT_TIMESTAMP
                WHERE payment_order_id = ? AND attempt_number = 1
                """, paymentId);
    }

    @Transactional
    public PaymentOrderResponse recordCheckout(UUID paymentId, CheckoutSession checkout) {
        PaymentRecord payment = lockPayment(paymentId);
        if (payment.providerPaymentIntentId() != null) {
            if (!payment.providerPaymentIntentId().equals(checkout.providerPaymentIntentId())) {
                throw new PaymentException(HttpStatus.CONFLICT, "PAYMENT_PROVIDER_INTENT_MISMATCH",
                        "The provider response did not match the existing payment intent");
            }
            return response(payment);
        }
        PaymentState next = checkout.state();
        if (next == SUCCEEDED) {
            next = REQUIRES_RECONCILIATION;
        }
        if (!payment.status().equals(next.name())) {
            transition(payment, next, PaymentTransitionSource.PROVIDER_RESPONSE, "PROVIDER_INTENT_CREATED");
        }
        jdbc.update("""
                UPDATE payment_orders
                SET provider_payment_intent_id = ?, provider_session_id = ?, checkout_url = ?,
                    payment_status = ?, updated_at = CURRENT_TIMESTAMP, failure_code = NULL
                WHERE payment_order_id = ?
                """, checkout.providerPaymentIntentId(), checkout.providerSessionId(), checkout.checkoutUrl().toString(),
                next.name(), paymentId);
        jdbc.update("""
                UPDATE payment_attempts
                SET provider_payment_intent_id = ?, provider_session_id = ?, attempt_status = ?
                WHERE payment_order_id = ? AND attempt_number = 1
                """, checkout.providerPaymentIntentId(), checkout.providerSessionId(), next.name(), paymentId);
        if (next == REQUIRES_RECONCILIATION) {
            recordReconciliation(paymentId, "UNVERIFIED_INTENT_RESPONSE");
        }
        return response(loadPayment(paymentId));
    }

    @Transactional
    public void recordInitiationFailure(UUID paymentId) {
        PaymentRecord payment = lockPayment(paymentId);
        if (payment.status().equals("INITIATED")) {
            transition(payment, REQUIRES_RECONCILIATION,
                    PaymentTransitionSource.PROVIDER_RESPONSE, "PROVIDER_INTENT_OUTCOME_UNKNOWN");
        }
        jdbc.update("""
                UPDATE payment_orders
                SET payment_status = CASE
                        WHEN payment_status = 'INITIATED' THEN 'REQUIRES_RECONCILIATION'
                        ELSE payment_status
                    END,
                    failure_code = 'PROVIDER_OUTCOME_UNKNOWN',
                    updated_at = CURRENT_TIMESTAMP
                WHERE payment_order_id = ?
                """, paymentId);
        jdbc.update("""
                UPDATE payment_attempts SET attempt_status = 'REQUIRES_RECONCILIATION'
                WHERE payment_order_id = ? AND attempt_number = 1
                """, paymentId);
        recordReconciliation(paymentId, "PROVIDER_OUTCOME_UNKNOWN");
    }

    @Transactional
    public void recordStatusRefreshFailure(UUID paymentId) {
        recordMismatch(paymentId, "PROVIDER_STATUS_OUTCOME_UNKNOWN");
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse readOwn(UUID paymentId, UUID ownerId) {
        PaymentRecord payment = jdbc.query("""
                SELECT payment_orders.payment_order_id, payment_orders.booking_id,
                       payment_orders.owner_user_id, bookings.booking_reference AS booking_reference,
                       payment_orders.merchant_reference, payment_orders.provider_payment_intent_id,
                       payment_orders.provider_session_id, payment_orders.checkout_url,
                       payment_orders.amount_minor, payment_orders.currency, payment_orders.payment_status,
                       payment_orders.expires_at, payment_orders.created_at, payment_orders.failure_code,
                       payment_orders.fare_quote_id, NULL::text AS train_number, NULL::text AS train_name,
                       NULL::text AS coach_class_code, 0 AS passenger_count, expires_at AS payment_deadline
                FROM payment_orders JOIN bookings USING (booking_id)
                WHERE payment_orders.payment_order_id = ? AND payment_orders.owner_user_id = ?
                """, result -> result.next() ? payment(result) : null, paymentId, ownerId);
        if (payment == null) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment was not found");
        }
        return response(payment);
    }

    @Transactional(readOnly = true)
    public PaymentRecord readForRefresh(UUID paymentId, UUID ownerId) {
        PaymentRecord payment = jdbc.query("""
                SELECT payment_orders.payment_order_id, payment_orders.booking_id,
                       payment_orders.owner_user_id, bookings.booking_reference AS booking_reference,
                       payment_orders.merchant_reference, payment_orders.provider_payment_intent_id,
                       payment_orders.provider_session_id, payment_orders.checkout_url,
                       payment_orders.amount_minor, payment_orders.currency, payment_orders.payment_status,
                       payment_orders.expires_at, payment_orders.created_at, payment_orders.failure_code,
                       payment_orders.fare_quote_id, NULL::text AS train_number, NULL::text AS train_name,
                       NULL::text AS coach_class_code, 0 AS passenger_count, expires_at AS payment_deadline
                FROM payment_orders JOIN bookings USING (booking_id)
                WHERE payment_orders.payment_order_id = ? AND payment_orders.owner_user_id = ?
                """, result -> result.next() ? payment(result) : null, paymentId, ownerId);
        if (payment == null) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment was not found");
        }
        return payment;
    }

    @Transactional
    public void recordProviderStatus(UUID paymentId, VerifiedProviderStatus status) {
        PaymentRecord payment = lockPayment(paymentId);
        if (!payment.providerPaymentIntentId().equals(status.providerPaymentIntentId())
                || !("RC-PAY-" + payment.bookingReference()).equals(status.merchantReference())
                || !payment.bookingReference().equals(status.bookingReference())
                || payment.amountMinor() != status.amountMinor()
                || !payment.currency().equals(status.currency())) {
            recordMismatch(paymentId, "PROVIDER_STATUS_MISMATCH");
            return;
        }
        PaymentState target = status.state();
        if (target == SUCCEEDED && !status.verified()) {
            recordMismatch(paymentId, "SUCCESS_NOT_AUTHORITATIVELY_VERIFIED");
            return;
        }
        PaymentState current = PaymentState.valueOf(payment.status());
        if (current == target) return;
        PaymentTransitionSource source = status.verified()
                ? PaymentTransitionSource.VERIFIED_STATUS_QUERY : PaymentTransitionSource.PROVIDER_RESPONSE;
        try {
            PaymentStateMachine.transition(current, target, source);
        } catch (IllegalStateException exception) {
            recordMismatch(paymentId, "INVALID_PROVIDER_STATE_TRANSITION");
            if (target == SUCCEEDED) {
                recordFinancialException(payment, "VERIFIED_SUCCESS_AFTER_TERMINAL_PAYMENT_STATE");
            }
            return;
        }
        if (target == SUCCEEDED && !bookingService.canConfirmAfterVerifiedPayment(payment.bookingId())) {
            recordMismatch(paymentId, "VERIFIED_PAYMENT_AFTER_BOOKING_EXPIRY");
            recordFinancialException(payment, "LATE_SUCCESS_AFTER_BOOKING_EXPIRY");
            return;
        }
        transition(payment, target, source,
                status.verified() ? "PROVIDER_STATUS_VERIFIED" : "PROVIDER_STATUS_REFRESHED");
        jdbc.update("""
                UPDATE payment_orders
                SET payment_status = ?, completed_at = ?, transaction_reference = ?,
                    failure_code = ?, updated_at = CURRENT_TIMESTAMP
                WHERE payment_order_id = ?
                """, target.name(), timestamp(status.completedAt()), status.transactionReference(),
                target == REQUIRES_RECONCILIATION ? "PROVIDER_STATUS_UNMAPPED" : null, paymentId);
        if (target == SUCCEEDED && !bookingService.confirmAfterVerifiedPayment(payment.bookingId())) {
            throw new PaymentException(HttpStatus.CONFLICT, "PAYMENT_CONFIRMATION_RACE",
                    "Payment status could not be safely matched to the booking");
        }
        if (target == SUCCEEDED) {
            ticketIssuanceService.enqueueConfirmedBooking(payment.bookingId());
        }
        jdbc.update("""
                UPDATE payment_attempts SET attempt_status = ?, completed_at = ?, failure_code = ?
                WHERE payment_order_id = ? AND attempt_number = 1
                """, target.name(), timestamp(status.completedAt()),
                target == FAILED ? "PROVIDER_PAYMENT_FAILED" : null, paymentId);
        if (target == SUCCEEDED) {
            jdbc.update("""
                    INSERT INTO payment_audit_records(
                        payment_audit_record_id, payment_order_id, event_type, details, correlation_id
                    ) VALUES (?, ?, 'PAYMENT_VERIFIED',
                        jsonb_build_object('transactionReference', ?, 'verifiedAt', ?), ?)
                    """, UUID.randomUUID(), paymentId, status.transactionReference(),
                    status.verifiedAt() == null ? null : status.verifiedAt().toString(), correlationId());
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentAdminSummary> adminSearch(String query, int page, int size) {
        String text = query == null || query.isBlank() ? null : query.trim();
        if (text != null && text.length() > 80) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "PAYMENT_SEARCH_INVALID",
                    "The payment search term is too long");
        }
        return jdbc.query("""
                SELECT payment_order_id, booking_id, merchant_reference,
                       provider_payment_intent_id, provider_code, payment_status, amount_minor,
                       currency, expires_at, created_at
                FROM payment_orders
                WHERE (? IS NULL OR payment_order_id::text = ?
                       OR merchant_reference ILIKE ? OR provider_payment_intent_id = ?)
                ORDER BY created_at DESC, payment_order_id
                LIMIT ? OFFSET ?
                """, (result, row) -> new PaymentAdminSummary(
                result.getObject("payment_order_id", UUID.class),
                result.getObject("booking_id", UUID.class),
                result.getString("merchant_reference").replace("RC-PAY-", ""),
                result.getString("provider_code"),
                result.getString("provider_payment_intent_id"),
                result.getString("payment_status"),
                PaymentAmount.fromMinorUnits(result.getLong("amount_minor")),
                result.getString("currency"),
                result.getTimestamp("expires_at").toInstant(),
                result.getTimestamp("created_at").toInstant()),
                text, text, text == null ? null : "%" + text + "%", text,
                Math.max(1, Math.min(size, 100)), (long) Math.max(page, 0) * Math.max(1, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public PaymentAdminDetails adminRead(UUID paymentId) {
        PaymentRecord payment = loadPayment(paymentId);
        if (payment == null) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment was not found");
        }
        PaymentAdminSummary summary = adminSearch(payment.paymentId().toString(), 0, 1)
                .stream().findFirst().orElseThrow(() ->
                        new PaymentException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment was not found"));
        return new PaymentAdminDetails(
                summary,
                payment.checkoutUrl(),
                jdbc.query("""
                        SELECT details ->> 'transactionReference'
                        FROM payment_audit_records
                        WHERE payment_order_id = ? AND event_type = 'PAYMENT_VERIFIED'
                        ORDER BY occurred_at DESC LIMIT 1
                        """, result -> result.next() ? result.getString(1) : null, paymentId),
                jsonRows("SELECT attempt_number, attempt_status, retry_count, provider_payment_intent_id, started_at, completed_at, failure_code FROM payment_attempts WHERE payment_order_id = ? ORDER BY attempt_number", paymentId),
                jsonRows("SELECT previous_status, new_status, source, reason_code, occurred_at FROM payment_status_history WHERE payment_order_id = ? ORDER BY occurred_at, payment_status_history_id", paymentId),
                jsonRows("SELECT provider_code, provider_event_id, signature_verified, processing_status, received_at, processed_at, failure_code FROM payment_webhook_events WHERE payment_order_id = ? ORDER BY received_at", paymentId),
                jsonRows("SELECT reconciliation_status, reason_code, observed_provider_status, next_attempt_at, created_at, completed_at FROM payment_reconciliation_records WHERE payment_order_id = ? ORDER BY created_at", paymentId),
                jsonRows("SELECT exception_code, exception_status, details::text, created_at, resolved_at FROM payment_financial_exceptions WHERE payment_order_id = ? ORDER BY created_at", paymentId));
    }

    private PaymentRecord loadPayment(UUID paymentId) {
        return jdbc.query("""
                SELECT payment_orders.payment_order_id, payment_orders.booking_id,
                       payment_orders.owner_user_id, bookings.booking_reference AS booking_reference,
                       payment_orders.merchant_reference, payment_orders.provider_payment_intent_id,
                       payment_orders.provider_session_id, payment_orders.checkout_url,
                       payment_orders.amount_minor, payment_orders.currency, payment_orders.payment_status,
                       payment_orders.expires_at, payment_orders.created_at, payment_orders.failure_code,
                       payment_orders.fare_quote_id, train_snapshot ->> 'trainNumber' AS train_number,
                       train_snapshot ->> 'trainName' AS train_name,
                       journey_snapshot ->> 'coachClassCode' AS coach_class_code,
                       (SELECT COUNT(*) FROM booking_passengers p
                        WHERE p.booking_id = bookings.booking_id) AS passenger_count,
                       payment_deadline
                FROM payment_orders JOIN bookings USING (booking_id)
                WHERE payment_order_id = ?
                """, result -> result.next() ? payment(result) : null, paymentId);
    }

    private void ensureFreshProviderAttemptAllowed(PaymentRecord payment, BookingForPayment booking) {
        if (payment.providerPaymentIntentId() != null
                || !List.of("CREATED", "INITIATED", "REQUIRES_RECONCILIATION").contains(payment.status())) {
            return;
        }
        if (!"AWAITING_PAYMENT".equals(booking.bookingStatus())
                || !booking.paymentDeadline().isAfter(databaseNow())) {
            throw new PaymentException(HttpStatus.CONFLICT, "BOOKING_NOT_PAYABLE",
                    "This booking is no longer awaiting payment");
        }
        ReservationDetails allocation = reservationService.readForBooking(booking.reservationRequestId());
        if (!"ALLOCATED_PENDING_BOOKING".equals(allocation.status())
                || allocation.expiresAt() == null
                || !allocation.expiresAt().isAfter(databaseNow())) {
            throw new PaymentException(HttpStatus.CONFLICT, "ALLOCATION_NOT_PAYABLE",
                    "A live provisional allocation is required to start payment");
        }
    }

    private PaymentRecord lockPayment(UUID paymentId) {
        PaymentRecord payment = jdbc.query("""
                SELECT payment_orders.payment_order_id, payment_orders.booking_id,
                       payment_orders.owner_user_id, bookings.booking_reference AS booking_reference,
                       payment_orders.merchant_reference,
                       payment_orders.provider_payment_intent_id, payment_orders.provider_session_id,
                       payment_orders.checkout_url, payment_orders.amount_minor, payment_orders.currency,
                       payment_orders.payment_status, payment_orders.expires_at, payment_orders.created_at,
                       payment_orders.failure_code, payment_orders.fare_quote_id,
                       bookings.train_snapshot ->> 'trainNumber' AS train_number,
                       bookings.train_snapshot ->> 'trainName' AS train_name,
                       bookings.journey_snapshot ->> 'coachClassCode' AS coach_class_code,
                       (SELECT COUNT(*) FROM booking_passengers p
                        WHERE p.booking_id = bookings.booking_id) AS passenger_count,
                       bookings.payment_deadline
                FROM payment_orders JOIN bookings USING (booking_id)
                WHERE payment_orders.payment_order_id = ?
                FOR UPDATE OF payment_orders
                """, result -> result.next() ? payment(result) : null, paymentId);
        if (payment == null) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment was not found");
        }
        return payment;
    }

    private void transition(
            PaymentRecord payment,
            PaymentState target,
            PaymentTransitionSource source,
            String reason
    ) {
        PaymentState current = PaymentState.valueOf(payment.status());
        PaymentStateMachine.transition(current, target, source);
        jdbc.update("""
                INSERT INTO payment_status_history(
                    payment_status_history_id, payment_order_id, previous_status, new_status,
                    source, reason_code, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), payment.paymentId(), current.name(), target.name(),
                historySource(source), reason, correlationId());
    }

    private void recordMismatch(UUID paymentId, String reason) {
        PaymentRecord payment = lockPayment(paymentId);
        if (SUCCEEDED.name().equals(payment.status())) {
            recordFinancialException(payment, "PAYMENT_MISMATCH_AFTER_SUCCESS");
        }
        if (!REQUIRES_RECONCILIATION.name().equals(payment.status())) {
            try {
                transition(payment, REQUIRES_RECONCILIATION,
                        PaymentTransitionSource.RECONCILIATION, reason);
                jdbc.update("""
                        UPDATE payment_orders SET payment_status = 'REQUIRES_RECONCILIATION',
                            failure_code = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE payment_order_id = ?
                        """, reason, paymentId);
            } catch (IllegalStateException exception) {
                jdbc.update("""
                        INSERT INTO payment_audit_records(
                            payment_audit_record_id, payment_order_id, event_type, details, correlation_id
                        ) VALUES (?, ?, 'PAYMENT_RECONCILIATION_REQUIRED', ?::jsonb, ?)
                        """, UUID.randomUUID(), paymentId, "{\"reason\":\"" + reason + "\"}", correlationId());
            }
        }
        recordReconciliation(paymentId, reason);
    }

    private void recordReconciliation(UUID paymentId, String reason) {
        jdbc.update("""
                INSERT INTO payment_reconciliation_records(
                    payment_reconciliation_record_id, payment_order_id,
                    reconciliation_status, reason_code, next_attempt_at
                ) VALUES (?, ?, 'PENDING', ?, CURRENT_TIMESTAMP + INTERVAL '30 seconds')
                """, UUID.randomUUID(), paymentId, reason);
    }

    private void recordFinancialException(PaymentRecord payment, String reason) {
        jdbc.update("""
                INSERT INTO payment_financial_exceptions(
                    payment_financial_exception_id, payment_order_id, booking_id,
                    exception_code, details
                ) VALUES (?, ?, ?, ?, jsonb_build_object('reason', ?))
                ON CONFLICT DO NOTHING
                """, UUID.randomUUID(), payment.paymentId(), payment.bookingId(), reason, reason);
    }

    private PaymentOrderResponse response(PaymentRecord payment) {
        return new PaymentOrderResponse(
                payment.paymentId(), payment.bookingId(), payment.bookingReference(),
                payment.status(), PaymentAmount.fromMinorUnits(payment.amountMinor()),
                payment.currency(), payment.expiresAt(),
                payment.checkoutUrl() == null ? null : URI.create(payment.checkoutUrl()),
                true, REQUIRES_RECONCILIATION.name().equals(payment.status()));
    }

    private String jsonRows(String sql, UUID paymentId) {
        return jdbc.query(sql, result -> {
            var metadata = result.getMetaData();
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
            while (result.next()) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    row.put(metadata.getColumnLabel(column), result.getObject(column));
                }
                rows.add(row);
            }
            try {
                return objectMapper.writeValueAsString(rows);
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("Payment audit data could not be serialized", exception);
            }
        }, paymentId);
    }

    private BookingForPayment booking(ResultSet result) throws SQLException {
        return new BookingForPayment(
                result.getObject("booking_id", UUID.class),
                result.getObject("owner_user_id", UUID.class),
                result.getString("booking_reference"),
                result.getString("status"),
                result.getBigDecimal("total_payable"),
                result.getString("currency"),
                result.getObject("fare_quote_id", UUID.class),
                result.getObject("reservation_request_id", UUID.class),
                result.getTimestamp("payment_deadline").toInstant(),
                result.getString("train_number"),
                result.getString("train_name"),
                result.getString("coach_class_code"),
                result.getInt("passenger_count"));
    }

    private PaymentRecord payment(ResultSet result) throws SQLException {
        return new PaymentRecord(
                result.getObject("payment_order_id", UUID.class),
                result.getObject("booking_id", UUID.class),
                result.getObject("owner_user_id", UUID.class),
                result.getString("booking_reference"),
                result.getString("provider_payment_intent_id"),
                result.getString("provider_session_id"),
                result.getString("checkout_url"),
                result.getLong("amount_minor"),
                result.getString("currency"),
                result.getString("payment_status"),
                result.getTimestamp("expires_at").toInstant(),
                result.getTimestamp("created_at").toInstant(),
                result.getString("failure_code"),
                result.getObject("fare_quote_id", UUID.class),
                result.getString("train_number"),
                result.getString("train_name"),
                result.getString("coach_class_code"),
                result.getInt("passenger_count"),
                result.getTimestamp("payment_deadline").toInstant());
    }

    private Instant databaseNow() {
        Timestamp now = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        if (now == null) throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE,
                "PAYMENT_TIME_UNAVAILABLE", "Payment time could not be verified");
        return now.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static URI validatePassengerOrigin(String origin) {
        URI uri = URI.create(origin);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || (!"https".equals(uri.getScheme())
                && !("http".equals(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost()))))) {
            throw new IllegalArgumentException("Passenger origin must be a trusted HTTP(S) origin");
        }
        return URI.create(uri.getScheme() + "://" + uri.getAuthority());
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "PAYMENT_IDEMPOTENCY_KEY_INVALID",
                    "A valid Idempotency-Key header is required");
        }
        return key;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private String correlationId() {
        String requestId = RequestIds.current();
        return requestId == null || "unavailable".equals(requestId)
                ? UUID.randomUUID().toString() : requestId;
    }

    private static String historySource(PaymentTransitionSource source) {
        return switch (source) {
            case SYSTEM -> "SYSTEM";
            case PROVIDER_RESPONSE -> "PROVIDER_RESPONSE";
            case VERIFIED_WEBHOOK -> "VERIFIED_WEBHOOK";
            case VERIFIED_STATUS_QUERY -> "STATUS_QUERY";
            case RECONCILIATION -> "RECONCILIATION";
            case EXPIRY_JOB -> "EXPIRY_JOB";
            case CHECKOUT_RETURN -> "STATUS_QUERY";
        };
    }

    private PreparedPayment prepared(
            PaymentRecord payment,
            BookingForPayment booking,
            UUID ownerId,
            String experience
    ) {
        URI success = URI.create(passengerOrigin + "/" + experience + "/payment/return?paymentId="
                + payment.paymentId() + "&outcome=success");
        URI failure = URI.create(passengerOrigin + "/" + experience + "/payment/return?paymentId="
                + payment.paymentId() + "&outcome=cancel");
        long remainingSeconds = Duration.between(databaseNow(), payment.expiresAt()).toSeconds();
        long seconds = payment.providerPaymentIntentId() == null
                ? remainingSeconds - PROVIDER_EXPIRY_SAFETY_BUFFER_SECONDS
                : Math.max(1, remainingSeconds);
        if (seconds <= 0) {
            throw new PaymentException(HttpStatus.CONFLICT, "PAYMENT_DEADLINE_TOO_CLOSE",
                    "The booking payment deadline is too close to start checkout", true);
        }
        return new PreparedPayment(
                payment.paymentId(), payment.bookingId(), ownerId, "RC-PAY-" + payment.bookingReference(),
                payment.bookingReference(), payment.providerPaymentIntentId(), payment.providerSessionId(),
                payment.checkoutUrl(), payment.amountMinor(), payment.currency(), payment.status(),
                payment.expiresAt(), payment.createdAt(), booking.fareQuoteId(), booking.trainNumber(),
                booking.trainName(), booking.coachClassCode(), booking.passengerCount(), experience,
                success, failure, Duration.ofSeconds(seconds), "railconnect-" + payment.paymentId());
    }

    public record PreparedPayment(
            UUID paymentId,
            UUID bookingId,
            UUID ownerId,
            String merchantReference,
            String bookingReference,
            String providerPaymentIntentId,
            String providerSessionId,
            String checkoutUrl,
            long amountMinor,
            String currency,
            String status,
            Instant expiresAt,
            Instant createdAt,
            UUID fareQuoteId,
            String trainNumber,
            String trainName,
            String coachClassCode,
            int passengerCount,
            String experience,
            URI successUrl,
            URI failureUrl,
            Duration expiresIn,
            String providerIdempotencyKey
    ) {
        public PaymentInitiationRequest toProviderRequest() {
            return new PaymentInitiationRequest(
                    paymentId,
                    merchantReference,
                    bookingReference,
                    ownerId.toString(),
                    "RailConnect Train " + trainNumber + " " + trainName + " - " + coachClassCode,
                    amountMinor,
                    currency,
                    expiresAt,
                    providerIdempotencyKey,
                    successUrl,
                    failureUrl,
                    expiresIn,
                    Map.of("train_number", trainNumber, "coach_class", coachClassCode,
                            "passenger_count", Integer.toString(passengerCount)));
        }
    }

    public record PaymentRecord(
            UUID paymentId,
            UUID bookingId,
            UUID ownerId,
            String bookingReference,
            String providerPaymentIntentId,
            String providerSessionId,
            String checkoutUrl,
            long amountMinor,
            String currency,
            String status,
            Instant expiresAt,
            Instant createdAt,
            String failureCode,
            UUID fareQuoteId,
            String trainNumber,
            String trainName,
            String coachClassCode,
            int passengerCount,
            Instant paymentDeadline
    ) { }

    private record BookingForPayment(
            UUID bookingId,
            UUID ownerId,
            String bookingReference,
            String bookingStatus,
            java.math.BigDecimal amount,
            String currency,
            UUID fareQuoteId,
            UUID reservationRequestId,
            Instant paymentDeadline,
            String trainNumber,
            String trainName,
            String coachClassCode,
            int passengerCount
    ) { }

    private record Idempotency(String fingerprint, UUID paymentId) { }
}
