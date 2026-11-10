package in.railconnect.core.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdvancedReservationModels {
    private AdvancedReservationModels() {
    }

    public record QuotaView(
            UUID quotaId,
            String quotaCode,
            String displayName,
            String description,
            String eligibilityCriteriaJson,
            boolean active
    ) {
    }

    public record QuotaAllocationInput(
            @NotNull UUID trainRunId,
            @NotBlank @Size(max = 32) String coachClassCode,
            @NotBlank @Size(max = 32) String quotaCode,
            @Min(0) int allocatedBerths,
            @Min(0) int racCapacity,
            @Min(0) int waitlistCapacity
    ) {
    }

    public record QuotaAllocationView(
            UUID allocationId,
            UUID trainRunId,
            String coachClassCode,
            String quotaCode,
            int allocatedBerths,
            int racCapacity,
            int waitlistCapacity,
            int confirmedCount,
            int racCount,
            int waitlistCount
    ) {
    }

    public record ReservationQueueView(
            UUID queueId,
            UUID trainRunId,
            String coachClassCode,
            String quotaCode,
            String queueType,
            int currentHighestSequence,
            int waitingCount
    ) {
    }

    public record WaitlistEntryView(
            UUID waitlistId,
            UUID bookingId,
            UUID bookingPassengerId,
            String pnr,
            String passengerName,
            String coachClassCode,
            String quotaCode,
            String waitlistCategory,
            int initialPosition,
            int currentPosition,
            int queueSequence,
            String status,
            Instant createdAt
    ) {
    }

    public record RacEntryView(
            UUID racId,
            UUID bookingId,
            UUID bookingPassengerId,
            String pnr,
            String passengerName,
            String coachClassCode,
            String quotaCode,
            int initialPosition,
            int currentPosition,
            int queueSequence,
            String assignedCoachNumber,
            String assignedBerthNumber,
            String status,
            Instant createdAt
    ) {
    }

    public record PromotionResult(
            UUID promotionId,
            UUID bookingPassengerId,
            String passengerName,
            String fromStatus,
            String toStatus,
            int fromPosition,
            int toPosition,
            String allocatedCoachNumber,
            String allocatedBerthNumber,
            Instant promotedAt
    ) {
    }

    public record PassengerReservationStatusView(
            UUID bookingId,
            String bookingReference,
            String pnr,
            String trainNumber,
            String trainName,
            String serviceDate,
            String coachClassCode,
            String quotaCode,
            String overallStatus,
            List<PassengerStatusItem> passengers,
            List<StatusHistoryItem> history
    ) {
    }

    public record PassengerStatusItem(
            UUID bookingPassengerId,
            int passengerNumber,
            String passengerName,
            String reservationStatus, // CONFIRMED, RAC, WAITLISTED, CANCELLED
            int positionNumber,
            String coachNumber,
            String berthNumber,
            String berthType
    ) {
    }

    public record StatusHistoryItem(
            String previousState,
            String newState,
            String reason,
            String details,
            Instant occurredAt
    ) {
    }

    public record ChartView(
            UUID chartId,
            UUID trainRunId,
            String trainNumber,
            String trainName,
            String serviceDate,
            String chartStatus,
            int totalPassengers,
            int confirmedPassengers,
            int racPassengers,
            int waitlistedPassengers,
            int currentVersion,
            Instant preparedAt,
            Instant finalizedAt,
            List<ChartPassengerView> passengers
    ) {
    }

    public record ChartPassengerView(
            UUID chartPassengerId,
            String pnr,
            String passengerName,
            String coachNumber,
            String berthNumber,
            String berthType,
            String coachClassCode,
            String reservationStatus,
            int positionNumber,
            String fromStationCode,
            String toStationCode
    ) {
    }

    public record ChartCorrectionRequest(
            @NotNull UUID chartPassengerId,
            @NotBlank @Size(max = 20) String newCoach,
            @NotBlank @Size(max = 20) String newBerth,
            @NotBlank @Size(max = 250) String reason
    ) {
    }
}

