package in.railconnect.core.operations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OperationsModels {
    private OperationsModels() {
    }

    public record OperationsDashboardSummary(
            int totalScheduledRuns,
            int runningTrains,
            int delayedTrains,
            int haltedTrains,
            int completedTrains,
            int cancelledTrains,
            int activeIncidents,
            int platformConflicts,
            List<OperationalAnnouncementView> activeAnnouncements,
            List<TrainMonitoringView> activeRuns
    ) {
    }

    public record TrainMonitoringView(
            UUID trainRunId,
            String trainNumber,
            String trainName,
            String serviceDate,
            String operationalStatus,
            String currentStationCode,
            String currentStationName,
            String nextStationCode,
            String nextStationName,
            String lastReportedEvent,
            Instant lastReportedAt,
            int delayMinutes,
            double routeProgressPercent,
            int totalStops,
            int completedStops
    ) {
    }

    public record StationOperationalUpdateInput(
            @NotNull UUID trainRunId,
            @NotNull UUID stationId,
            int stopIndex,
            @NotBlank @Pattern(regexp = "ARRIVAL|DEPARTURE|PASSING") String eventType,
            @NotNull Instant scheduledTime,
            @NotNull Instant reportedActualTime,
            String platformNumber,
            int haltDurationMinutes,
            String remarks
    ) {
    }

    public record StationOperationalUpdateView(
            UUID updateId,
            UUID trainRunId,
            UUID stationId,
            String stationCode,
            String stationName,
            int stopIndex,
            String eventType,
            Instant scheduledTime,
            Instant reportedActualTime,
            int delayMinutes,
            String platformNumber,
            int haltDurationMinutes,
            String remarks,
            String reportedByName,
            Instant reportedAt
    ) {
    }

    public record PlatformView(
            UUID platformId,
            UUID stationId,
            String stationCode,
            String platformNumber,
            String trackNumber,
            int lengthMeters,
            boolean electrified,
            String status
    ) {
    }

    public record PlatformAssignmentInput(
            @NotNull UUID stationId,
            @NotNull UUID platformId,
            @NotNull UUID trainRunId,
            @NotNull Instant scheduledArrival,
            @NotNull Instant scheduledDeparture
    ) {
    }

    public record PlatformAssignmentView(
            UUID assignmentId,
            UUID stationId,
            UUID platformId,
            String platformNumber,
            UUID trainRunId,
            String trainNumber,
            String trainName,
            Instant scheduledArrival,
            Instant scheduledDeparture,
            Instant actualArrival,
            Instant actualDeparture,
            String assignmentStatus,
            boolean conflictFlag
    ) {
    }

    public record IncidentInput(
            @NotBlank @Size(max = 160) String title,
            @NotBlank String description,
            @NotBlank @Pattern(regexp = "BREAKDOWN|TECHNICAL_ISSUE|STATION_ISSUE|SAFETY_INCIDENT|ROUTE_DISRUPTION|PLATFORM_CONFLICT|SERVICE_DELAY|COMMUNICATION") String category,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String priority,
            UUID trainRunId,
            UUID stationId
    ) {
    }

    public record IncidentView(
            UUID incidentId,
            String incidentNumber,
            String title,
            String description,
            String category,
            String priority,
            String status,
            UUID trainRunId,
            String trainNumber,
            UUID stationId,
            String stationName,
            String reportedByName,
            String resolutionNotes,
            Instant createdAt,
            Instant resolvedAt
    ) {
    }

    public record DisruptionInput(
            @NotNull UUID trainRunId,
            @NotBlank @Pattern(regexp = "DELAY|TEMPORARY_HALT|DIVERSION|STATION_SKIPPED|PLATFORM_SHIFT|TRAIN_CANCELLED|RESTORED") String disruptionType,
            UUID affectedStationId,
            int expectedDurationMinutes,
            @NotBlank String reason,
            String operationalImpact
    ) {
    }

    public record DisruptionView(
            UUID disruptionId,
            UUID trainRunId,
            String trainNumber,
            String disruptionType,
            String stationName,
            int expectedDurationMinutes,
            String reason,
            String operationalImpact,
            String status,
            Instant startTime,
            Instant resolvedAt,
            PassengerImpactAssessmentView impactAssessment
    ) {
    }

    public record PassengerImpactAssessmentView(
            UUID assessmentId,
            int affectedPassengersCount,
            int affectedConfirmedCount,
            int affectedRacCount,
            int affectedWaitlistCount,
            String summary,
            String actionTaken,
            Instant assessedAt
    ) {
    }

    public record OperationalAnnouncementInput(
            @NotBlank @Size(max = 160) String title,
            @NotBlank String content,
            @NotBlank @Pattern(regexp = "DELAY|PLATFORM_CHANGE|ARRIVAL_DEPARTURE|DIVERSION|CANCELLATION|STATION_NOTICE|GENERAL") String announcementType,
            @NotBlank @Pattern(regexp = "NORMAL|URGENT|CRITICAL") String priority,
            @NotBlank @Pattern(regexp = "ALL|TRAIN|STATION") String targetScope,
            UUID targetTrainRunId,
            UUID targetStationId,
            Instant expiresAt
    ) {
    }

    public record OperationalAnnouncementView(
            UUID announcementId,
            String title,
            String content,
            String announcementType,
            String priority,
            String targetScope,
            UUID targetTrainRunId,
            UUID targetStationId,
            String status,
            Instant startsAt,
            Instant expiresAt,
            Instant createdAt
    ) {
    }

    public record PublicTrainTrackingView(
            String trainNumber,
            String trainName,
            String serviceDate,
            String operationalStatus,
            int currentDelayMinutes,
            double routeProgressPercent,
            String currentStationCode,
            String currentStationName,
            String nextStationCode,
            String nextStationName,
            Instant lastReportedAt,
            String dataReliabilityStatus, // e.g. "STAFF_VERIFIED", "AWAITING_UPDATE", "SCHEDULED"
            List<TrackingStopItem> stops,
            List<OperationalAnnouncementView> relevantAnnouncements
    ) {
    }

    public record TrackingStopItem(
            int stopIndex,
            String stationCode,
            String stationName,
            String scheduledArrival,
            String scheduledDeparture,
            String actualArrival,
            String actualDeparture,
            int delayMinutes,
            String platformNumber,
            String stopStatus // "COMPLETED", "CURRENT", "UPCOMING", "SKIPPED"
    ) {
    }
}
