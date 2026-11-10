package in.railconnect.core.cancellation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CancellationModels {
    private CancellationModels() {
    }

    public record CancellationEligibility(
            UUID bookingId,
            String bookingReference,
            String pnr,
            boolean eligible,
            String ineligibilityReason,
            String bookingStatus,
            String ticketStatus,
            LocalDate journeyDate,
            String trainNumber,
            String trainName,
            Instant departureTime,
            double hoursBeforeDeparture,
            int totalPassengers,
            BigDecimal totalFarePaid,
            String currency,
            List<EligiblePassenger> passengers
    ) {}

    public record EligiblePassenger(
            UUID bookingPassengerId,
            UUID ticketPassengerId,
            int passengerNumber,
            String passengerName,
            String coachNumber,
            String berthNumber,
            String berthType,
            BigDecimal farePaid,
            boolean eligible
    ) {}

    public record CancellationQuoteRequest(
            List<UUID> bookingPassengerIds
    ) {}

    public record CancellationQuote(
            UUID bookingId,
            String bookingReference,
            String pnr,
            int policyVersionNumber,
            String policyName,
            String appliedTierCode,
            double hoursBeforeDeparture,
            BigDecimal originalFare,
            BigDecimal cancellationFee,
            BigDecimal refundableAmount,
            String currency,
            Instant calculatedAt,
            List<PassengerQuoteBreakdown> passengers
    ) {}

    public record PassengerQuoteBreakdown(
            UUID bookingPassengerId,
            String passengerName,
            String coachNumber,
            String berthNumber,
            BigDecimal farePaid,
            BigDecimal cancellationFee,
            BigDecimal refundableAmount,
            String appliedTierCode
    ) {}

    public record CancelBookingRequest(
            List<UUID> bookingPassengerIds,
            String reason,
            String idempotencyKey
    ) {}

    public record CancellationReceipt(
            UUID cancellationId,
            String cancellationReference,
            UUID bookingId,
            String bookingReference,
            String pnr,
            String cancellationType,
            String cancellationStatus,
            int policyVersionNumber,
            double hoursBeforeDeparture,
            BigDecimal totalOriginalAmount,
            BigDecimal totalCancellationFee,
            BigDecimal totalRefundableAmount,
            String currency,
            String reason,
            Instant cancelledAt,
            RefundSummary refundSummary,
            List<PassengerQuoteBreakdown> cancelledPassengers
    ) {}

    public record RefundSummary(
            UUID refundOrderId,
            String merchantRefundRef,
            String providerCode,
            String providerRefundId,
            BigDecimal amount,
            String currency,
            String refundStatus,
            boolean verified,
            Instant completedAt
    ) {}

    public record CancellationPage(
            List<CancellationSummary> items,
            int page,
            int size,
            long totalElements
    ) {}

    public record CancellationSummary(
            UUID cancellationId,
            String cancellationReference,
            String bookingReference,
            String pnr,
            String cancellationType,
            String cancellationStatus,
            BigDecimal totalRefundableAmount,
            String currency,
            String refundStatus,
            Instant createdAt
    ) {}

    public record RefundOrderView(
            UUID refundOrderId,
            UUID cancellationId,
            String cancellationReference,
            UUID bookingId,
            String bookingReference,
            String pnr,
            String merchantRefundRef,
            String providerCode,
            String providerRefundId,
            String paymentIntentId,
            BigDecimal amount,
            String currency,
            String refundStatus,
            boolean verified,
            Instant verifiedAt,
            Instant completedAt,
            String failureCode,
            String failureMessage,
            Instant createdAt,
            List<RefundAttemptRecord> attempts,
            List<RefundHistoryRecord> statusHistory
    ) {}

    public record RefundAttemptRecord(
            UUID refundAttemptId,
            int attemptNumber,
            String idempotencyKey,
            int responseCode,
            String status,
            Instant attemptedAt,
            long durationMs
    ) {}

    public record RefundHistoryRecord(
            UUID refundStatusHistoryId,
            String previousStatus,
            String newStatus,
            String source,
            String reasonCode,
            Instant occurredAt
    ) {}

    public record CancellationPolicyView(
            UUID policyVersionId,
            int versionNumber,
            String policyName,
            String description,
            String status,
            String rulesJson,
            Instant createdAt,
            Instant publishedAt
    ) {}

    public record PolicyCreateRequest(
            String policyName,
            String description,
            String rulesJson
    ) {}
}

