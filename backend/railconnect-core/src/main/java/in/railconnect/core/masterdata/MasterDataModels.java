package in.railconnect.core.masterdata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MasterDataModels {
    private MasterDataModels() {
    }

    public record PageResult<T>(List<T> items, int page, int size, long totalItems) {
    }

    public record DashboardStats(
            long totalStations,
            long activeStations,
            long totalTrains,
            long activeTrains,
            long publishedRoutes,
            long coachClasses,
            long configuredCoaches
    ) {
    }

    public record FacilityInput(
            @NotBlank @Size(max = 40) String facilityCode,
            Map<String, Object> details,
            boolean active
    ) {
    }

    public record StationRequest(
            @NotBlank @Size(max = 20) String stationCode,
            @NotBlank @Size(max = 160) String stationName,
            @Size(max = 20) List<@NotBlank @Size(max = 120) String> alternateNames,
            @NotBlank @Size(max = 120) String city,
            @NotBlank @Size(max = 120) String state,
            @NotBlank @Size(max = 80) String country,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @NotBlank @Size(max = 64) String timezone,
            @Size(max = 1000) String address,
            UUID zoneId,
            UUID divisionId,
            @Size(max = 40) String stationCategory,
            @Size(max = 40) List<@Valid FacilityInput> facilities
    ) {
    }

    public record StationStatusRequest(@NotBlank String status, @Size(max = 500) String reason) {
    }

    public record StationVerificationRequest(boolean verified, @Size(max = 500) String reason) {
    }

    public record Station(
            UUID stationId,
            String stationCode,
            String stationName,
            List<String> alternateNames,
            String city,
            String state,
            String country,
            BigDecimal latitude,
            BigDecimal longitude,
            String timezone,
            String address,
            UUID zoneId,
            UUID divisionId,
            String stationCategory,
            boolean codeVerified,
            String status,
            List<FacilityInput> facilities,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record StationVersion(
            UUID stationVersionId,
            UUID stationId,
            int versionNumber,
            String versionType,
            Map<String, Object> snapshot,
            String changeReason,
            UUID changedBy,
            Instant createdAt
    ) {
    }

    public record LookupRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 400) String description
    ) {
    }

    public record Lookup(UUID id, String code, String name, String description, boolean active) {
    }

    public record TrainRequest(
            @NotBlank @Size(max = 32) String trainNumber,
            @NotBlank @Size(max = 160) String trainName,
            @NotNull UUID categoryId,
            @NotNull UUID trainTypeId,
            @NotNull UUID sourceStationId,
            @NotNull UUID destinationStationId,
            UUID routeId,
            @DecimalMin("0.01") BigDecimal totalDistanceKm,
            @Size(max = 1000) String description,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }

    public record TrainStatusRequest(@NotBlank String status, @Size(max = 500) String reason) {
    }

    public record Train(
            UUID trainId,
            String trainNumber,
            String trainName,
            UUID categoryId,
            String categoryName,
            UUID trainTypeId,
            String trainTypeName,
            UUID sourceStationId,
            String sourceStationCode,
            String sourceStationName,
            UUID destinationStationId,
            String destinationStationCode,
            String destinationStationName,
            UUID routeId,
            BigDecimal totalDistanceKm,
            String description,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
    }

    public record RouteStopRequest(
            @NotNull UUID stationId,
            @Positive int sequence,
            LocalTime arrivalTime,
            LocalTime departureTime,
            @PositiveOrZero @Max(Short.MAX_VALUE) int arrivalDayOffset,
            @PositiveOrZero @Max(Short.MAX_VALUE) int departureDayOffset,
            @PositiveOrZero int haltMinutes,
            @NotNull @DecimalMin("0.0") BigDecimal distanceFromSourceKm,
            @NotBlank @Size(max = 24) String stopType,
            @Size(max = 24) String platformLabel
    ) {
    }

    public record RouteVersionRequest(
            @NotBlank @Size(max = 160) String routeName,
            @NotNull UUID sourceStationId,
            @NotNull UUID destinationStationId,
            @NotNull @DecimalMin("0.01") BigDecimal totalDistanceKm,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 500) String changeReason,
            @NotEmpty @Size(min = 2, max = 200) List<@Valid RouteStopRequest> stops
    ) {
    }

    public record RouteStop(
            UUID routeStopId,
            UUID stationId,
            String stationCode,
            String stationName,
            int sequence,
            LocalTime arrivalTime,
            LocalTime departureTime,
            int arrivalDayOffset,
            int departureDayOffset,
            int haltMinutes,
            BigDecimal distanceFromSourceKm,
            String stopType,
            String platformLabel
    ) {
    }

    public record RouteVersion(
            UUID routeVersionId,
            UUID routeId,
            String routeName,
            int versionNumber,
            UUID sourceStationId,
            String sourceStationCode,
            UUID destinationStationId,
            String destinationStationCode,
            BigDecimal totalDistanceKm,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            List<RouteStop> stops
    ) {
    }

    public record ScheduleExceptionInput(
            @NotNull LocalDate serviceDate,
            @NotBlank String serviceStatus,
            @Size(max = 300) String changeReason
    ) {
    }

    public record ScheduleRequest(
            @NotNull UUID trainId,
            @NotNull UUID routeVersionId,
            @NotBlank @Size(max = 64) String timezone,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(min = 1, max = 7) List<@Positive Integer> operatingDays,
            @Size(max = 366) List<@Valid ScheduleExceptionInput> exceptions,
            @Size(max = 500) String changeReason
    ) {
    }

    public record Schedule(
            UUID scheduleId,
            UUID trainId,
            UUID routeVersionId,
            int versionNumber,
            String timezone,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            List<Integer> operatingDays,
            List<ScheduleExceptionInput> exceptions
    ) {
    }

    public record CoachClassRequest(
            @NotBlank @Size(max = 32) String classCode,
            @NotBlank @Size(max = 100) String className,
            @Size(max = 400) String description,
            @NotBlank String accommodationType,
            @Size(max = 40) String fareCategory,
            boolean active
    ) {
    }

    public record CoachTypeRequest(
            @NotBlank @Size(max = 32) String typeCode,
            @NotBlank @Size(max = 100) String typeName,
            @Size(max = 400) String description,
            boolean active
    ) {
    }

    public record AccommodationInput(
            @NotBlank @Size(max = 20) String number,
            @NotBlank String type,
            Map<String, Object> position,
            @Positive Integer deckLevel,
            @Positive int displayOrder,
            boolean active
    ) {
    }

    public record CoachConfigurationRequest(
            @NotBlank @Size(max = 40) String configurationCode,
            @NotBlank @Size(max = 120) String configurationName,
            @NotNull UUID coachClassId,
            @NotNull UUID coachTypeId,
            @Positive int capacity,
            @NotEmpty @Size(max = 500) List<@Valid AccommodationInput> accommodations,
            @Size(max = 500) String changeReason
    ) {
    }

    public record CoachConfiguration(
            UUID configurationId,
            String configurationCode,
            String configurationName,
            UUID coachClassId,
            String coachClassName,
            UUID coachTypeId,
            String coachTypeName,
            int capacity,
            boolean active,
            int layoutVersion,
            String layoutStatus,
            List<AccommodationInput> accommodations
    ) {
    }

    public record CoachRequest(
            @NotBlank @Size(max = 40) String coachCode,
            @NotNull UUID configurationId
    ) {
    }

    public record CoachSummary(
            UUID coachId,
            String coachCode,
            UUID configurationId,
            String configurationName,
            String className,
            String coachTypeName,
            boolean active
    ) {
    }

    public record CoachLayoutRequest(
            @NotEmpty @Size(max = 500) List<@Valid AccommodationInput> accommodations,
            @Size(max = 500) String changeReason
    ) {
    }

    public record CompositionCoachInput(
            @NotNull UUID coachId,
            @NotBlank @Size(max = 20) String coachNumber,
            @Positive int position
    ) {
    }

    public record CompositionRequest(
            @NotEmpty @Size(max = 100) List<@Valid CompositionCoachInput> coaches,
            @Size(max = 500) String changeReason
    ) {
    }

    public record CompositionCoach(
            UUID coachId,
            String coachNumber,
            int position,
            int capacity,
            UUID configurationId,
            String configurationName,
            String className,
            String coachTypeName
    ) {
    }

    public record Composition(
            UUID compositionId,
            UUID trainId,
            UUID currentVersionId,
            int versionNumber,
            String status,
            int totalCapacity,
            List<CompositionCoach> coaches
    ) {
    }

    public record AuditEntry(
            UUID auditId,
            UUID actorUserId,
            String entityType,
            UUID entityId,
            String action,
            String previousValue,
            String newValue,
            String changeReason,
            Instant occurredAt,
            String correlationId
    ) {
    }

    public record StatusRequest(@NotBlank String status, @Size(max = 500) String reason) {
    }
}
