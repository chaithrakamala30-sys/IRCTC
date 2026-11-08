package in.railconnect.core.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReservationModels {
    private ReservationModels() {
    }

    public record CreateReservationRequest(
            @NotNull UUID trainRunId,
            @NotNull LocalDate journeyDate,
            @NotNull UUID fromStationId,
            @NotNull UUID toStationId,
            @NotBlank @Size(max = 32) String coachClassCode,
            boolean groupSeatingRequested,
            @NotNull @Size(min = 1, max = 4) List<@Valid PassengerInput> passengers,
            @NotNull @Size(min = 1, max = 4) List<@NotNull UUID> inventoryHoldIds
    ) {
    }

    public record PassengerInput(
            @NotBlank @Size(max = 120) String displayName,
            @Pattern(regexp = "LOWER_BERTH|MIDDLE_BERTH|UPPER_BERTH|SIDE_LOWER_BERTH|SIDE_UPPER_BERTH")
            String preferredBerthType
    ) {
    }

    public record ReleaseRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9_]{3,64}") String reasonCode
    ) {
    }

    public record PassengerAllocation(
            UUID reservationPassengerId,
            int passengerNumber,
            String displayName,
            String preferredBerthType,
            String preferenceResult,
            UUID inventoryHoldId,
            UUID reservationAllocationId,
            UUID inventoryBerthId,
            String coachNumber,
            String berthNumber,
            String coachClassCode,
            String status
    ) {
    }

    public record StateChange(
            String previousStatus,
            String newStatus,
            String reasonCode,
            Instant occurredAt
    ) {
    }

    public record ReservationDetails(
            UUID reservationRequestId,
            UUID trainRunId,
            LocalDate journeyDate,
            UUID fromStationId,
            String fromStationName,
            UUID toStationId,
            String toStationName,
            String coachClassCode,
            boolean groupSeatingRequested,
            String groupSeatingResult,
            String status,
            Integer allocationScore,
            Integer allocationPolicyVersion,
            boolean provisional,
            Instant expiresAt,
            String failureCode,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            List<PassengerAllocation> passengers,
            List<StateChange> stateHistory
    ) {
    }

    public record ReservationSummary(
            UUID reservationRequestId,
            UUID trainRunId,
            LocalDate journeyDate,
            String coachClassCode,
            String status,
            int passengerCount,
            boolean groupSeatingRequested,
            Instant expiresAt,
            Instant createdAt
    ) {
    }

    public record AllocationCounts(String status, long count) {
    }

    public record AllocationPolicyRequest(
            @NotNull @Min(0) @Max(10000) Integer preferredBerthWeight,
            @NotNull @Min(0) @Max(10000) Integer sameCoachWeight,
            @NotNull @Min(0) @Max(10000) Integer nearbyBerthWeight
    ) {
    }

    public record AllocationPolicyVersion(
            int versionNumber,
            String status,
            int preferredBerthWeight,
            int sameCoachWeight,
            int nearbyBerthWeight,
            String createdByName,
            String approvedByName,
            Instant createdAt,
            Instant approvedAt,
            Instant publishedAt
    ) {
    }

    public record AllocationPolicyAuditEvent(
            UUID reservationPolicyAuditId,
            int versionNumber,
            String actorName,
            String eventType,
            String details,
            Instant occurredAt,
            String correlationId
    ) {
    }

    public record AllocationAuditEvent(
            UUID reservationAuditId,
            UUID reservationRequestId,
            String actorName,
            String eventType,
            String details,
            Instant occurredAt,
            String correlationId
    ) {
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
    }
}
