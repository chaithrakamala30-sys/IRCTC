package in.railconnect.core.cancellation;

import in.railconnect.core.cancellation.CancellationModels.*;
import in.railconnect.core.cancellation.CancellationPolicyEngine.ActivePolicy;
import in.railconnect.core.cancellation.CancellationPolicyEngine.FeeCalculation;
import in.railconnect.core.cancellation.RefundProvider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class CancellationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CancellationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final CancellationPolicyEngine policyEngine;
    private final DhruvaBankRefundAdapter refundAdapter;

    public CancellationService(
            JdbcTemplate jdbc,
            CancellationPolicyEngine policyEngine,
            DhruvaBankRefundAdapter refundAdapter
    ) {
        this.jdbc = jdbc;
        this.policyEngine = policyEngine;
        this.refundAdapter = refundAdapter;
    }

    private static String generateCancellationReference() {
        long number = 1000000000L + (long) (RANDOM.nextDouble() * 8999999999L);
        return "CAN-" + number;
    }

    private static String generateMerchantRefundRef() {
        long number = 100000L + (long) (RANDOM.nextDouble() * 899999L);
        return "RC-REF-" + number;
    }

    // ========================================================================
    // 1. Check Eligibility
    // ========================================================================
    @Transactional(readOnly = true)
    public CancellationEligibility checkEligibility(UUID bookingId, UUID ownerId) {
        BookingContext ctx = loadBookingContext(bookingId, ownerId);

        boolean eligible = true;
        String ineligibilityReason = null;

        if (!"CONFIRMED".equalsIgnoreCase(ctx.bookingStatus())) {
            eligible = false;
            ineligibilityReason = "Only CONFIRMED bookings can be cancelled";
        } else if ("CANCELLED".equalsIgnoreCase(ctx.ticketStatus())) {
            eligible = false;
            ineligibilityReason = "This booking/ticket has already been cancelled";
        } else if (ctx.hoursBeforeDeparture() <= 0) {
            eligible = false;
            ineligibilityReason = "Journey has already departed; cancellation is closed";
        }

        List<EligiblePassenger> paxList = loadEligiblePassengers(bookingId, eligible);

        return new CancellationEligibility(
                ctx.bookingId(),
                ctx.bookingReference(),
                ctx.pnr(),
                eligible,
                ineligibilityReason,
                ctx.bookingStatus(),
                ctx.ticketStatus(),
                ctx.journeyDate(),
                ctx.trainNumber(),
                ctx.trainName(),
                ctx.departureTime(),
                ctx.hoursBeforeDeparture(),
                paxList.size(),
                ctx.totalPayable(),
                ctx.currency(),
                paxList
        );
    }

    // ========================================================================
    // 2. Cancellation Quote
    // ========================================================================
    @Transactional(readOnly = true)
    public CancellationQuote calculateQuote(UUID bookingId, List<UUID> selectedPaxIds, UUID ownerId) {
        BookingContext ctx = loadBookingContext(bookingId, ownerId);
        if (!"CONFIRMED".equalsIgnoreCase(ctx.bookingStatus()) || "CANCELLED".equalsIgnoreCase(ctx.ticketStatus())) {
            throw new CancellationException(HttpStatus.CONFLICT, "BOOKING_NOT_CANCELLABLE",
                    "Only active confirmed bookings can be quoted for cancellation");
        }

        ActivePolicy policy = policyEngine.loadActivePolicy();
        List<PassengerFareRecord> passengers = loadPassengerFares(bookingId, selectedPaxIds);

        BigDecimal totalOriginalFare = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalRefundable = BigDecimal.ZERO;
        String firstTierCode = "TIER_RULE";

        List<PassengerQuoteBreakdown> breakdowns = new ArrayList<>();
        for (PassengerFareRecord p : passengers) {
            FeeCalculation feeCalc = policyEngine.calculatePassengerFee(
                    p.farePaid(),
                    ctx.coachClassCode(),
                    ctx.hoursBeforeDeparture(),
                    policy
            );
            firstTierCode = feeCalc.appliedTierCode();
            totalOriginalFare = totalOriginalFare.add(feeCalc.farePaid());
            totalFee = totalFee.add(feeCalc.cancellationFee());
            totalRefundable = totalRefundable.add(feeCalc.refundableAmount());

            breakdowns.add(new PassengerQuoteBreakdown(
                    p.bookingPassengerId(),
                    p.displayName(),
                    p.coachNumber(),
                    p.berthNumber(),
                    feeCalc.farePaid(),
                    feeCalc.cancellationFee(),
                    feeCalc.refundableAmount(),
                    feeCalc.appliedTierCode()
            ));
        }

        return new CancellationQuote(
                ctx.bookingId(),
                ctx.bookingReference(),
                ctx.pnr(),
                policy.versionNumber(),
                policy.policyName(),
                firstTierCode,
                ctx.hoursBeforeDeparture(),
                totalOriginalFare.setScale(2, RoundingMode.HALF_UP),
                totalFee.setScale(2, RoundingMode.HALF_UP),
                totalRefundable.setScale(2, RoundingMode.HALF_UP),
                ctx.currency(),
                Instant.now(),
                breakdowns
        );
    }

    // ========================================================================
    // 3. Confirm Cancellation & Execute Refund
    // ========================================================================
    @Transactional
    public CancellationReceipt cancelBooking(
            UUID bookingId,
            CancelBookingRequest request,
            UUID ownerId
    ) {
        String key = (request.idempotencyKey() == null || request.idempotencyKey().isBlank())
                ? "can_idemp_" + UUID.randomUUID()
                : request.idempotencyKey().trim();

        // 1. Idempotency Check
        Optional<CancellationReceipt> existing = checkExistingCancellation(ownerId, key);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Lock Booking and Ticket Rows
        BookingContext ctx = lockBookingContext(bookingId, ownerId);
        if (!"CONFIRMED".equalsIgnoreCase(ctx.bookingStatus())) {
            throw new CancellationException(HttpStatus.CONFLICT, "BOOKING_NOT_CANCELLABLE",
                    "Booking cannot be cancelled because its status is " + ctx.bookingStatus());
        }
        if ("CANCELLED".equalsIgnoreCase(ctx.ticketStatus())) {
            throw new CancellationException(HttpStatus.CONFLICT, "TICKET_ALREADY_CANCELLED",
                    "This ticket has already been cancelled");
        }
        if (ctx.hoursBeforeDeparture() <= 0) {
            throw new CancellationException(HttpStatus.CONFLICT, "JOURNEY_ALREADY_DEPARTED",
                    "Cannot cancel journey after scheduled departure");
        }

        ActivePolicy policy = policyEngine.loadActivePolicy();
        List<PassengerFareRecord> targetPax = loadPassengerFares(bookingId, request.bookingPassengerIds());
        boolean isFullCancellation = targetPax.size() == countTotalPassengers(bookingId);

        // 3. Calculate authoritative fees
        BigDecimal totalOriginalFare = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalRefundable = BigDecimal.ZERO;

        List<PassengerQuoteBreakdown> breakdowns = new ArrayList<>();
        for (PassengerFareRecord p : targetPax) {
            FeeCalculation feeCalc = policyEngine.calculatePassengerFee(
                    p.farePaid(),
                    ctx.coachClassCode(),
                    ctx.hoursBeforeDeparture(),
                    policy
            );
            totalOriginalFare = totalOriginalFare.add(feeCalc.farePaid());
            totalFee = totalFee.add(feeCalc.cancellationFee());
            totalRefundable = totalRefundable.add(feeCalc.refundableAmount());

            breakdowns.add(new PassengerQuoteBreakdown(
                    p.bookingPassengerId(),
                    p.displayName(),
                    p.coachNumber(),
                    p.berthNumber(),
                    feeCalc.farePaid(),
                    feeCalc.cancellationFee(),
                    feeCalc.refundableAmount(),
                    feeCalc.appliedTierCode()
            ));
        }

        UUID cancellationId = UUID.randomUUID();
        String cancellationRef = generateCancellationReference();
        Instant now = Instant.now();

        // 4. Record cancellation request
        jdbc.update("""
                INSERT INTO cancellation_requests (
                    cancellation_id, cancellation_reference, booking_id, ticket_id, owner_user_id,
                    cancellation_type, cancellation_status, policy_version_id, hours_before_departure,
                    departure_time, total_original_amount, total_cancellation_fee, total_refundable_amount,
                    currency, reason, idempotency_key, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                cancellationId,
                cancellationRef,
                bookingId,
                ctx.ticketId(),
                ownerId,
                isFullCancellation ? "FULL" : "PARTIAL",
                policy.policyVersionId(),
                BigDecimal.valueOf(ctx.hoursBeforeDeparture()).setScale(2, RoundingMode.HALF_UP),
                Timestamp.from(ctx.departureTime()),
                totalOriginalFare,
                totalFee,
                totalRefundable,
                ctx.currency(),
                request.reason() != null ? request.reason() : "Passenger requested cancellation",
                key,
                Timestamp.from(now)
        );

        // 5. Record individual cancelled passengers & release inventory safely
        for (PassengerQuoteBreakdown pax : breakdowns) {
            UUID paxId = UUID.randomUUID();
            PassengerAllocationInfo allocInfo = loadAllocationInfo(pax.bookingPassengerId());

            jdbc.update("""
                    INSERT INTO cancellation_passengers (
                        cancellation_passenger_id, cancellation_id, booking_passenger_id, ticket_passenger_id,
                        reservation_allocation_id, passenger_name, coach_number, berth_number,
                        fare_paid, cancellation_fee, refundable_amount, applied_tier_code, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    paxId,
                    cancellationId,
                    pax.bookingPassengerId(),
                    allocInfo.ticketPassengerId(),
                    allocInfo.reservationAllocationId(),
                    pax.passengerName(),
                    pax.coachNumber(),
                    pax.berthNumber(),
                    pax.farePaid(),
                    pax.cancellationFee(),
                    pax.refundableAmount(),
                    pax.appliedTierCode(),
                    Timestamp.from(now)
            );

            // Release physical berth segments from inventory
            releasePassengerBerthInventory(cancellationId, allocInfo, ownerId);
        }

        // 6. Transition Booking and Ticket status
        if (isFullCancellation) {
            jdbc.update("UPDATE bookings SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP WHERE booking_id = ?", bookingId);
            jdbc.update("INSERT INTO booking_status_history (booking_status_history_id, booking_id, previous_status, new_status, reason_code, correlation_id) VALUES (?, ?, 'CONFIRMED', 'CANCELLED', 'PASSENGER_CANCELLED', ?)",
                    UUID.randomUUID(), bookingId, cancellationRef);
            jdbc.update("INSERT INTO booking_audit_log (booking_audit_id, booking_id, actor_user_id, event_type, details, correlation_id) VALUES (?, ?, ?, 'BOOKING_CANCELLED', CAST(? AS jsonb), ?)",
                    UUID.randomUUID(), bookingId, ownerId, "{\"cancellationRef\":\"" + cancellationRef + "\"}", cancellationRef);

            jdbc.update("UPDATE tickets SET ticket_status = 'CANCELLED' WHERE ticket_id = ?", ctx.ticketId());
            jdbc.update("INSERT INTO ticket_audit_logs (ticket_audit_log_id, ticket_id, actor_user_id, action, details, correlation_id) VALUES (?, ?, ?, 'TICKET_CANCELLED', CAST(? AS jsonb), ?)",
                    UUID.randomUUID(), ctx.ticketId(), ownerId, "{\"cancellationRef\":\"" + cancellationRef + "\"}", cancellationRef);

            // Release reservation request
            jdbc.update("UPDATE reservation_requests SET status = 'RELEASED', updated_at = CURRENT_TIMESTAMP WHERE reservation_request_id = ?", ctx.reservationRequestId());
        }

        // 7. Initiate Dhruva Bank Refund Order
        UUID refundOrderId = UUID.randomUUID();
        String merchantRefundRef = generateMerchantRefundRef();
        PaymentOrderInfo paymentInfo = loadPaymentOrderInfo(bookingId);

        String initialRefundStatus = totalRefundable.compareTo(BigDecimal.ZERO) > 0 ? "REQUESTED" : "NOT_ELIGIBLE";

        jdbc.update("""
                INSERT INTO refund_orders (
                    refund_order_id, cancellation_id, payment_order_id, booking_id, owner_user_id,
                    merchant_refund_ref, provider_code, payment_intent_id, amount_minor, amount,
                    currency, refund_status, reason, idempotency_key, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'DHRUVA_BANK', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                refundOrderId,
                cancellationId,
                paymentInfo.paymentOrderId(),
                bookingId,
                ownerId,
                merchantRefundRef,
                paymentInfo.providerPaymentIntentId(),
                totalRefundable.multiply(BigDecimal.valueOf(100)).longValue(),
                totalRefundable,
                ctx.currency(),
                initialRefundStatus,
                request.reason(),
                "ref_idemp_" + cancellationRef,
                Timestamp.from(now),
                Timestamp.from(now)
        );

        RefundSummary refundSummary;
        if (totalRefundable.compareTo(BigDecimal.ZERO) > 0) {
            // Call refund adapter
            refundSummary = executeDhruvaRefund(
                    refundOrderId,
                    paymentInfo.providerPaymentIntentId(),
                    merchantRefundRef,
                    cancellationRef,
                    totalRefundable,
                    ctx.currency(),
                    request.reason()
            );
        } else {
            refundSummary = new RefundSummary(
                    refundOrderId,
                    merchantRefundRef,
                    "DHRUVA_BANK",
                    null,
                    BigDecimal.ZERO,
                    ctx.currency(),
                    "NOT_ELIGIBLE",
                    true,
                    now
            );
        }

        return new CancellationReceipt(
                cancellationId,
                cancellationRef,
                bookingId,
                ctx.bookingReference(),
                ctx.pnr(),
                isFullCancellation ? "FULL" : "PARTIAL",
                "CONFIRMED",
                policy.versionNumber(),
                ctx.hoursBeforeDeparture(),
                totalOriginalFare,
                totalFee,
                totalRefundable,
                ctx.currency(),
                request.reason(),
                now,
                refundSummary,
                breakdowns
        );
    }

    // ========================================================================
    // 4. Inventory Release Helper
    // ========================================================================
    private void releasePassengerBerthInventory(UUID cancellationId, PassengerAllocationInfo alloc, UUID actorId) {
        // Delete segment claims
        int claimsDeleted = jdbc.update(
                "DELETE FROM inventory_segment_claims WHERE reservation_allocation_id = ?",
                alloc.reservationAllocationId()
        );

        // Update allocation to RELEASED
        jdbc.update(
                "UPDATE reservation_allocations SET status = 'RELEASED', updated_at = CURRENT_TIMESTAMP WHERE reservation_allocation_id = ?",
                alloc.reservationAllocationId()
        );

        // Log to inventory_release_history
        jdbc.update("""
                INSERT INTO inventory_release_history (
                    release_history_id, cancellation_id, train_run_id, inventory_berth_id,
                    reservation_allocation_id, from_stop_index, to_stop_index, segments_released,
                    released_by, released_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                cancellationId,
                alloc.trainRunId(),
                alloc.inventoryBerthId(),
                alloc.reservationAllocationId(),
                alloc.fromStopIndex(),
                alloc.toStopIndex(),
                claimsDeleted > 0 ? claimsDeleted : (alloc.toStopIndex() - alloc.fromStopIndex()),
                actorId
        );
    }

    // ========================================================================
    // 5. Execute Refund with Dhruva Bank
    // ========================================================================
    private RefundSummary executeDhruvaRefund(
            UUID refundOrderId,
            String paymentIntentId,
            String merchantRefundRef,
            String cancellationReference,
            BigDecimal amount,
            String currency,
            String reason
    ) {
        String attemptKey = "att_" + merchantRefundRef + "_1";
        long startTime = System.currentTimeMillis();

        try {
            RefundExecutionResult result = refundAdapter.executeRefund(new RefundRequest(
                    refundOrderId,
                    paymentIntentId,
                    merchantRefundRef,
                    cancellationReference,
                    amount,
                    currency,
                    reason,
                    attemptKey
            ));

            long duration = System.currentTimeMillis() - startTime;
            Instant completed = result.completedAt() != null ? result.completedAt() : Instant.now();

            jdbc.update("""
                    UPDATE refund_orders
                    SET provider_refund_id = ?, refund_status = ?, completed_at = ?, verified = TRUE,
                        verified_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE refund_order_id = ?
                    """,
                    result.providerRefundId(), result.status(), Timestamp.from(completed), refundOrderId);

            jdbc.update("""
                    INSERT INTO refund_status_history (
                        refund_status_history_id, refund_order_id, previous_status, new_status,
                        source, reason_code, correlation_id
                    ) VALUES (?, ?, 'REQUESTED', ?, 'PROVIDER_RESPONSE', 'REFUND_SETTLED', ?)
                    """,
                    UUID.randomUUID(), refundOrderId, result.status(), merchantRefundRef);

            jdbc.update("""
                    INSERT INTO refund_attempts (
                        refund_attempt_id, refund_order_id, attempt_number, idempotency_key,
                        request_payload, response_code, response_payload, status, duration_ms
                    ) VALUES (?, ?, 1, ?, CAST(? AS jsonb), 200, CAST(? AS jsonb), ?, ?)
                    """,
                    UUID.randomUUID(), refundOrderId, attemptKey,
                    "{\"amount\":" + amount + "}", 
                    "{\"status\":\"" + result.status() + "\"}",
                    result.status(), duration);

            return new RefundSummary(
                    refundOrderId,
                    merchantRefundRef,
                    "DHRUVA_BANK",
                    result.providerRefundId(),
                    amount,
                    currency,
                    result.status(),
                    true,
                    completed
            );
        } catch (Exception e) {
            LOGGER.error("Refund dispatch failed for order {}: {}", refundOrderId, e.getMessage());
            long duration = System.currentTimeMillis() - startTime;

            jdbc.update("""
                    UPDATE refund_orders
                    SET refund_status = 'REQUIRES_RECONCILIATION', failure_code = 'PROVIDER_ERROR',
                        failure_message = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE refund_order_id = ?
                    """,
                    e.getMessage(), refundOrderId);

            jdbc.update("""
                    INSERT INTO refund_status_history (
                        refund_status_history_id, refund_order_id, previous_status, new_status,
                        source, reason_code, correlation_id
                    ) VALUES (?, ?, 'REQUESTED', 'REQUIRES_RECONCILIATION', 'SYSTEM', 'DISPATCH_FAILURE', ?)
                    """,
                    UUID.randomUUID(), refundOrderId, merchantRefundRef);

            return new RefundSummary(
                    refundOrderId,
                    merchantRefundRef,
                    "DHRUVA_BANK",
                    null,
                    amount,
                    currency,
                    "REQUIRES_RECONCILIATION",
                    false,
                    null
            );
        }
    }

    // ========================================================================
    // 6. Lookups & Audits
    // ========================================================================
    @Transactional(readOnly = true)
    public CancellationReceipt getCancellation(UUID cancellationId, UUID ownerId) {
        CancellationReceipt receipt = jdbc.query("""
                SELECT c.cancellation_id, c.cancellation_reference, c.booking_id, b.booking_reference,
                       t.pnr, c.cancellation_type, c.cancellation_status, p.version_number AS policy_version_number,
                       c.hours_before_departure, c.total_original_amount, c.total_cancellation_fee,
                       c.total_refundable_amount, c.currency, c.reason, c.created_at,
                       r.refund_order_id, r.merchant_refund_ref, r.provider_code, r.provider_refund_id,
                       r.amount AS refund_amount, r.refund_status, r.verified AS refund_verified, r.completed_at AS refund_completed_at
                FROM cancellation_requests c
                JOIN bookings b ON b.booking_id = c.booking_id
                JOIN tickets t ON t.ticket_id = c.ticket_id
                JOIN cancellation_policy_versions p ON p.policy_version_id = c.policy_version_id
                LEFT JOIN refund_orders r ON r.cancellation_id = c.cancellation_id
                WHERE c.cancellation_id = ? AND (? IS NULL OR c.owner_user_id = ?)
                """, rs -> {
            if (!rs.next()) return null;

            RefundSummary refSum = null;
            if (rs.getObject("refund_order_id") != null) {
                refSum = new RefundSummary(
                        rs.getObject("refund_order_id", UUID.class),
                        rs.getString("merchant_refund_ref"),
                        rs.getString("provider_code"),
                        rs.getString("provider_refund_id"),
                        rs.getBigDecimal("refund_amount"),
                        rs.getString("currency"),
                        rs.getString("refund_status"),
                        rs.getBoolean("refund_verified"),
                        rs.getTimestamp("refund_completed_at") != null ? rs.getTimestamp("refund_completed_at").toInstant() : null
                );
            }

            return new CancellationReceipt(
                    rs.getObject("cancellation_id", UUID.class),
                    rs.getString("cancellation_reference"),
                    rs.getObject("booking_id", UUID.class),
                    rs.getString("booking_reference"),
                    rs.getString("pnr"),
                    rs.getString("cancellation_type"),
                    rs.getString("cancellation_status"),
                    rs.getInt("policy_version_number"),
                    rs.getDouble("hours_before_departure"),
                    rs.getBigDecimal("total_original_amount"),
                    rs.getBigDecimal("total_cancellation_fee"),
                    rs.getBigDecimal("total_refundable_amount"),
                    rs.getString("currency"),
                    rs.getString("reason"),
                    rs.getTimestamp("created_at").toInstant(),
                    refSum,
                    List.of()
            );
        }, cancellationId, ownerId, ownerId);

        if (receipt == null) {
            throw new CancellationException(HttpStatus.NOT_FOUND, "CANCELLATION_NOT_FOUND", "Cancellation record not found");
        }

        List<PassengerQuoteBreakdown> pax = jdbc.query("""
                SELECT booking_passenger_id, passenger_name, coach_number, berth_number,
                       fare_paid, cancellation_fee, refundable_amount, applied_tier_code
                FROM cancellation_passengers
                WHERE cancellation_id = ?
                ORDER BY created_at
                """, (rs, row) -> new PassengerQuoteBreakdown(
                rs.getObject("booking_passenger_id", UUID.class),
                rs.getString("passenger_name"),
                rs.getString("coach_number"),
                rs.getString("berth_number"),
                rs.getBigDecimal("fare_paid"),
                rs.getBigDecimal("cancellation_fee"),
                rs.getBigDecimal("refundable_amount"),
                rs.getString("applied_tier_code")
        ), cancellationId);

        return new CancellationReceipt(
                receipt.cancellationId(),
                receipt.cancellationReference(),
                receipt.bookingId(),
                receipt.bookingReference(),
                receipt.pnr(),
                receipt.cancellationType(),
                receipt.cancellationStatus(),
                receipt.policyVersionNumber(),
                receipt.hoursBeforeDeparture(),
                receipt.totalOriginalAmount(),
                receipt.totalCancellationFee(),
                receipt.totalRefundableAmount(),
                receipt.currency(),
                receipt.reason(),
                receipt.cancelledAt(),
                receipt.refundSummary(),
                pax
        );
    }

    @Transactional(readOnly = true)
    public RefundOrderView getRefund(UUID refundOrderId, UUID ownerId) {
        RefundOrderView view = jdbc.query("""
                SELECT r.refund_order_id, r.cancellation_id, c.cancellation_reference,
                       r.booking_id, b.booking_reference, t.pnr, r.merchant_refund_ref,
                       r.provider_code, r.provider_refund_id, r.payment_intent_id,
                       r.amount, r.currency, r.refund_status, r.verified, r.verified_at,
                       r.completed_at, r.failure_code, r.failure_message, r.created_at
                FROM refund_orders r
                JOIN cancellation_requests c ON c.cancellation_id = r.cancellation_id
                JOIN bookings b ON b.booking_id = r.booking_id
                JOIN tickets t ON t.ticket_id = c.ticket_id
                WHERE r.refund_order_id = ? AND (? IS NULL OR r.owner_user_id = ?)
                """, rs -> {
            if (!rs.next()) return null;
            return new RefundOrderView(
                    rs.getObject("refund_order_id", UUID.class),
                    rs.getObject("cancellation_id", UUID.class),
                    rs.getString("cancellation_reference"),
                    rs.getObject("booking_id", UUID.class),
                    rs.getString("booking_reference"),
                    rs.getString("pnr"),
                    rs.getString("merchant_refund_ref"),
                    rs.getString("provider_code"),
                    rs.getString("provider_refund_id"),
                    rs.getString("payment_intent_id"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency"),
                    rs.getString("refund_status"),
                    rs.getBoolean("verified"),
                    rs.getTimestamp("verified_at") != null ? rs.getTimestamp("verified_at").toInstant() : null,
                    rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
                    rs.getString("failure_code"),
                    rs.getString("failure_message"),
                    rs.getTimestamp("created_at").toInstant(),
                    List.of(),
                    List.of()
            );
        }, refundOrderId, ownerId, ownerId);

        if (view == null) {
            throw new CancellationException(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "Refund order not found");
        }

        List<RefundHistoryRecord> history = jdbc.query("""
                SELECT refund_status_history_id, previous_status, new_status, source, reason_code, occurred_at
                FROM refund_status_history
                WHERE refund_order_id = ?
                ORDER BY occurred_at DESC
                """, (rs, row) -> new RefundHistoryRecord(
                rs.getObject("refund_status_history_id", UUID.class),
                rs.getString("previous_status"),
                rs.getString("new_status"),
                rs.getString("source"),
                rs.getString("reason_code"),
                rs.getTimestamp("occurred_at").toInstant()
        ), refundOrderId);

        return new RefundOrderView(
                view.refundOrderId(), view.cancellationId(), view.cancellationReference(),
                view.bookingId(), view.bookingReference(), view.pnr(), view.merchantRefundRef(),
                view.providerCode(), view.providerRefundId(), view.paymentIntentId(),
                view.amount(), view.currency(), view.refundStatus(), view.verified(),
                view.verifiedAt(), view.completedAt(), view.failureCode(), view.failureMessage(),
                view.createdAt(), List.of(), history
        );
    }

    @Transactional
    public RefundOrderView reconcileRefund(UUID refundOrderId) {
        RefundOrderView view = getRefund(refundOrderId, null);
        if ("SUCCEEDED".equalsIgnoreCase(view.refundStatus()) && view.verified()) {
            return view;
        }

        if (view.providerRefundId() != null) {
            RefundVerificationResult ver = refundAdapter.verifyRefund(view.providerRefundId());
            if (ver.verified() && "SUCCEEDED".equalsIgnoreCase(ver.status())) {
                jdbc.update("""
                        UPDATE refund_orders
                        SET refund_status = 'SUCCEEDED', verified = TRUE, verified_at = CURRENT_TIMESTAMP,
                            completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP
                        WHERE refund_order_id = ?
                        """, refundOrderId);

                jdbc.update("""
                        INSERT INTO refund_status_history (
                            refund_status_history_id, refund_order_id, previous_status, new_status,
                            source, reason_code, correlation_id
                        ) VALUES (?, ?, ?, 'SUCCEEDED', 'RECONCILIATION', 'ADMIN_RECONCILED', ?)
                        """,
                        UUID.randomUUID(), refundOrderId, view.refundStatus(), view.merchantRefundRef());
            }
        }
        return getRefund(refundOrderId, null);
    }

    // ========================================================================
    // Internal Helper records & queries
    // ========================================================================
    private record BookingContext(
            UUID bookingId,
            String bookingReference,
            UUID ticketId,
            String pnr,
            String bookingStatus,
            String ticketStatus,
            UUID reservationRequestId,
            LocalDate journeyDate,
            String coachClassCode,
            String trainNumber,
            String trainName,
            BigDecimal totalPayable,
            String currency,
            Instant departureTime,
            double hoursBeforeDeparture
    ) {}

    private BookingContext loadBookingContext(UUID bookingId, UUID ownerId) {
        return queryBookingContext(bookingId, ownerId, false);
    }

    private BookingContext lockBookingContext(UUID bookingId, UUID ownerId) {
        return queryBookingContext(bookingId, ownerId, true);
    }

    private BookingContext queryBookingContext(UUID bookingId, UUID ownerId, boolean lock) {
        String sql = """
                SELECT b.booking_id, b.booking_reference, t.ticket_id, t.pnr,
                       b.status AS booking_status, t.ticket_status, b.reservation_request_id,
                       b.journey_date, b.coach_class_code,
                       b.train_snapshot ->> 'trainNumber' AS train_number,
                       b.train_snapshot ->> 'trainName' AS train_name,
                       b.total_payable, b.currency,
                       b.journey_date + COALESCE((
                           SELECT s.departure_time
                           FROM train_route_stops s
                           JOIN train_route_versions rv ON rv.route_version_id = s.route_version_id
                           JOIN trains tr ON tr.route_id = rv.route_id
                           WHERE tr.train_number = (b.train_snapshot ->> 'trainNumber')
                             AND s.station_id = b.from_station_id
                           LIMIT 1
                       ), '08:00:00'::time) AS departure_timestamp
                FROM bookings b
                LEFT JOIN tickets t ON t.booking_id = b.booking_id
                WHERE b.booking_id = ? AND b.owner_user_id = ?
                """ + (lock ? " FOR UPDATE OF b" : "");

        BookingContext ctx = jdbc.query(sql, rs -> {
            if (!rs.next()) return null;
            Instant dep = rs.getTimestamp("departure_timestamp") != null
                    ? rs.getTimestamp("departure_timestamp").toInstant()
                    : Instant.now().plus(Duration.ofDays(2));
            double hoursBefore = Duration.between(Instant.now(), dep).toMinutes() / 60.0;

            return new BookingContext(
                    rs.getObject("booking_id", UUID.class),
                    rs.getString("booking_reference"),
                    rs.getObject("ticket_id", UUID.class),
                    rs.getString("pnr"),
                    rs.getString("booking_status"),
                    rs.getString("ticket_status") != null ? rs.getString("ticket_status") : "ISSUED",
                    rs.getObject("reservation_request_id", UUID.class),
                    rs.getDate("journey_date").toLocalDate(),
                    rs.getString("coach_class_code"),
                    rs.getString("train_number"),
                    rs.getString("train_name"),
                    rs.getBigDecimal("total_payable"),
                    rs.getString("currency"),
                    dep,
                    hoursBefore
            );
        }, bookingId, ownerId);

        if (ctx == null) {
            throw new CancellationException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking was not found or does not belong to user");
        }
        return ctx;
    }

    private List<EligiblePassenger> loadEligiblePassengers(UUID bookingId, boolean bookingEligible) {
        return jdbc.query("""
                SELECT bp.booking_passenger_id, tp.ticket_passenger_id, bp.passenger_number,
                       bp.display_name, bp.coach_number, bp.berth_number, bp.berth_type,
                       tp.fare_paid
                FROM booking_passengers bp
                JOIN ticket_passengers tp ON tp.booking_passenger_id = bp.booking_passenger_id
                WHERE bp.booking_id = ?
                ORDER BY bp.passenger_number
                """, (rs, row) -> new EligiblePassenger(
                rs.getObject("booking_passenger_id", UUID.class),
                rs.getObject("ticket_passenger_id", UUID.class),
                rs.getInt("passenger_number"),
                rs.getString("display_name"),
                rs.getString("coach_number"),
                rs.getString("berth_number"),
                rs.getString("berth_type"),
                rs.getBigDecimal("fare_paid"),
                bookingEligible
        ), bookingId);
    }

    private record PassengerFareRecord(
            UUID bookingPassengerId,
            String displayName,
            String coachNumber,
            String berthNumber,
            BigDecimal farePaid
    ) {}

    private List<PassengerFareRecord> loadPassengerFares(UUID bookingId, List<UUID> selectedPaxIds) {
        String baseSql = """
                SELECT bp.booking_passenger_id, bp.display_name, bp.coach_number, bp.berth_number, tp.fare_paid
                FROM booking_passengers bp
                JOIN ticket_passengers tp ON tp.booking_passenger_id = bp.booking_passenger_id
                WHERE bp.booking_id = ?
                """;

        if (selectedPaxIds == null || selectedPaxIds.isEmpty()) {
            return jdbc.query(baseSql + " ORDER BY bp.passenger_number", (rs, row) -> new PassengerFareRecord(
                    rs.getObject("booking_passenger_id", UUID.class),
                    rs.getString("display_name"),
                    rs.getString("coach_number"),
                    rs.getString("berth_number"),
                    rs.getBigDecimal("fare_paid")
            ), bookingId);
        }

        List<Object> params = new ArrayList<>();
        params.add(bookingId);
        params.addAll(selectedPaxIds);
        String inClause = String.join(",", Collections.nCopies(selectedPaxIds.size(), "?"));

        return jdbc.query(baseSql + " AND bp.booking_passenger_id IN (" + inClause + ") ORDER BY bp.passenger_number",
                (rs, row) -> new PassengerFareRecord(
                        rs.getObject("booking_passenger_id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("coach_number"),
                        rs.getString("berth_number"),
                        rs.getBigDecimal("fare_paid")
                ), params.toArray());
    }

    private int countTotalPassengers(UUID bookingId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM booking_passengers WHERE booking_id = ?",
                Integer.class,
                bookingId
        );
        return count != null ? count : 0;
    }

    private record PassengerAllocationInfo(
            UUID ticketPassengerId,
            UUID reservationAllocationId,
            UUID trainRunId,
            UUID inventoryBerthId,
            int fromStopIndex,
            int toStopIndex
    ) {}

    private PassengerAllocationInfo loadAllocationInfo(UUID bookingPassengerId) {
        return jdbc.query("""
                SELECT tp.ticket_passenger_id, bp.reservation_allocation_id,
                       ra.train_run_id, ra.inventory_berth_id, ra.from_stop_index, ra.to_stop_index
                FROM booking_passengers bp
                JOIN ticket_passengers tp ON tp.booking_passenger_id = bp.booking_passenger_id
                JOIN reservation_allocations ra ON ra.reservation_allocation_id = bp.reservation_allocation_id
                WHERE bp.booking_passenger_id = ?
                """, rs -> {
            if (!rs.next()) {
                throw new CancellationException(HttpStatus.INTERNAL_SERVER_ERROR, "ALLOCATION_INFO_MISSING",
                        "Passenger allocation data missing for release");
            }
            return new PassengerAllocationInfo(
                    rs.getObject("ticket_passenger_id", UUID.class),
                    rs.getObject("reservation_allocation_id", UUID.class),
                    rs.getObject("train_run_id", UUID.class),
                    rs.getObject("inventory_berth_id", UUID.class),
                    rs.getInt("from_stop_index"),
                    rs.getInt("to_stop_index")
            );
        }, bookingPassengerId);
    }

    private record PaymentOrderInfo(
            UUID paymentOrderId,
            String providerPaymentIntentId
    ) {}

    private PaymentOrderInfo loadPaymentOrderInfo(UUID bookingId) {
        return jdbc.query("""
                SELECT payment_order_id, provider_payment_intent_id
                FROM payment_orders
                WHERE booking_id = ? AND payment_status = 'SUCCEEDED'
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                throw new CancellationException(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_ORDER_NOT_FOUND",
                        "No successful payment found for this booking to refund");
            }
            return new PaymentOrderInfo(
                    rs.getObject("payment_order_id", UUID.class),
                    rs.getString("provider_payment_intent_id")
            );
        }, bookingId);
    }

    private Optional<CancellationReceipt> checkExistingCancellation(UUID ownerId, String idempotencyKey) {
        UUID existingId = jdbc.query("""
                SELECT cancellation_id FROM cancellation_requests
                WHERE owner_user_id = ? AND idempotency_key = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                ownerId, idempotencyKey);

        if (existingId != null) {
            return Optional.of(getCancellation(existingId, ownerId));
        }
        return Optional.empty();
    }
}

