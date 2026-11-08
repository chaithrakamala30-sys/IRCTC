package in.railconnect.core.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InventoryModels {
    private InventoryModels() {
    }

    public record InitializeRunRequest(
            @NotNull UUID trainId,
            @NotNull LocalDate serviceDate
    ) {
    }

    public record JourneyRequest(
            @NotNull UUID trainRunId,
            @NotNull UUID fromStationId,
            @NotNull UUID toStationId,
            @Size(max = 32) String coachClassCode
    ) {
    }

    public record HoldRequest(
            @NotNull UUID trainRunId,
            @NotNull UUID fromStationId,
            @NotNull UUID toStationId,
            @NotBlank @Size(max = 32) String coachClassCode,
            @Pattern(regexp = "LOWER_BERTH|MIDDLE_BERTH|UPPER_BERTH|SIDE_LOWER_BERTH|SIDE_UPPER_BERTH")
            String preferredBerthType
    ) {
    }

    public record BlockRequest(
            @NotNull UUID inventoryBerthId,
            @NotNull UUID fromStationId,
            @NotNull UUID toStationId,
            @NotBlank @Pattern(regexp = "BLOCKED|MAINTENANCE|UNAVAILABLE") String blockType,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record RunSummary(
            UUID trainRunId,
            UUID trainId,
            String trainNumber,
            String trainName,
            LocalDate serviceDate,
            String status,
            int stops,
            int coaches,
            int berths,
            Instant initializedAt
    ) {
    }

    public record TrainOption(UUID trainId, String trainNumber, String trainName) {
    }

    public record Stop(
            int stopIndex,
            UUID stationId,
            String stationCode,
            String stationName,
            int stopSequence,
            String arrivalTime,
            String departureTime,
            int arrivalDayOffset,
            int departureDayOffset,
            double distanceFromSourceKm
    ) {
    }

    public record RunDetails(RunSummary run, List<Stop> stops) {
    }

    public record ClassAvailability(
            String coachClassCode,
            String coachClassName,
            int totalBerths,
            int available,
            int held,
            int blocked,
            int unavailable,
            String status
    ) {
    }

    public record AvailabilityResponse(
            UUID trainRunId,
            LocalDate serviceDate,
            UUID fromStationId,
            String fromStationName,
            UUID toStationId,
            String toStationName,
            int fromStopIndex,
            int toStopIndex,
            double distanceKm,
            List<ClassAvailability> classes
    ) {
    }

    public record Berth(
            UUID inventoryBerthId,
            String coachNumber,
            int coachPosition,
            String coachClassCode,
            String coachClassName,
            String berthNumber,
            String berthType,
            int displayOrder,
            String status
    ) {
    }

    public record BerthPage(List<Berth> items, int page, int size, long total) {
    }

    public record HoldResponse(
            UUID inventoryHoldId,
            UUID trainRunId,
            UUID inventoryBerthId,
            String coachNumber,
            String coachClassCode,
            String berthNumber,
            UUID fromStationId,
            String fromStationName,
            UUID toStationId,
            String toStationName,
            String status,
            Instant createdAt,
            Instant expiresAt
    ) {
    }

    public record BlockResponse(
            UUID inventoryBlockId,
            UUID trainRunId,
            UUID inventoryBerthId,
            String coachNumber,
            String berthNumber,
            String blockType,
            String reason,
            String status,
            Instant createdAt
    ) {
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
    }

    public record AuditEvent(
            UUID inventoryAuditId,
            UUID trainRunId,
            UUID inventoryBerthId,
            String actorName,
            String eventType,
            String details,
            Instant occurredAt,
            String correlationId
    ) {
    }
}
