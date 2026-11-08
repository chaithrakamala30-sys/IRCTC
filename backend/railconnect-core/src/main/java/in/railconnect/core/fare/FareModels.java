package in.railconnect.core.fare;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FareModels {
    private FareModels() {
    }

    public record FareRuleVersionRequest(
            @NotBlank @Size(max = 160) String ruleName,
            @NotBlank @Size(max = 500) String changeReason,
            UUID trainId,
            UUID categoryId,
            UUID coachClassId,
            @NotBlank String pricingModel,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal baseAmount,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 8, fraction = 4) BigDecimal perKmRate,
            @Min(0) @Max(999) int priority,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull @Size(max = 100) List<@Valid DistanceSlabRequest> distanceSlabs,
            @NotNull @Size(max = 100) List<@Valid FareComponentRequest> components,
            @NotNull @Size(max = 100) List<@Valid PassengerAdjustmentRequest> passengerAdjustments
    ) {
    }

    public record DistanceSlabRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 7, fraction = 2) BigDecimal minimumDistanceKm,
            @DecimalMin("0.01") @Digits(integer = 7, fraction = 2) BigDecimal maximumDistanceKm,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal baseAmount,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 8, fraction = 4) BigDecimal perKmRate
    ) {
    }

    public record FareComponentRequest(
            UUID passengerCategoryId,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String componentCode,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank String componentType,
            @NotBlank String calculationMethod,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 10, fraction = 4) BigDecimal amount,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal maximumAmount,
            @NotNull @DecimalMin("0.00") @Digits(integer = 7, fraction = 2) BigDecimal minimumDistanceKm,
            @Min(0) @Max(999) int calculationOrder,
            boolean enabled
    ) {
    }

    public record PassengerAdjustmentRequest(
            @NotNull UUID passengerCategoryId,
            @NotBlank String adjustmentType,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 10, fraction = 4) BigDecimal adjustmentValue,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal maximumAmount
    ) {
    }

    public record FareVersionSummary(
            UUID fareRuleId,
            String ruleName,
            UUID fareRuleVersionId,
            int versionNumber,
            String status,
            UUID trainId,
            String trainNumber,
            UUID categoryId,
            String categoryCode,
            UUID coachClassId,
            String coachClassCode,
            String pricingModel,
            String currency,
            BigDecimal baseAmount,
            BigDecimal perKmRate,
            int priority,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String changeReason,
            String createdBy,
            Instant createdAt,
            String approvedBy,
            Instant approvedAt,
            String publishedBy,
            Instant publishedAt
    ) {
    }

    public record FarePassengerCategory(
            UUID passengerCategoryId,
            String categoryCode,
            String categoryName,
            Integer minimumAge,
            Integer maximumAge,
            boolean active
    ) {
    }

    public record PassengerCategoryRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String categoryCode,
            @NotBlank @Size(max = 100) String categoryName,
            @Min(0) @Max(120) Integer minimumAge,
            @Min(0) @Max(120) Integer maximumAge
    ) {
    }

    public record FareQuoteRequest(
            @NotNull UUID trainId,
            @NotNull UUID fromStationId,
            @NotNull UUID toStationId,
            @NotNull LocalDate journeyDate,
            @NotBlank @Size(max = 32) String coachClassCode,
            @NotEmpty @Size(max = 20) List<@Valid PassengerRequest> passengers
    ) {
    }

    public record PassengerRequest(
            @NotBlank @Size(max = 32) String categoryCode,
            @Min(0) @Max(120) Integer age
    ) {
    }

    public record FareLineItem(
            String code,
            String label,
            String type,
            BigDecimal amount
    ) {
    }

    public record PassengerFare(
            String categoryCode,
            String categoryName,
            List<FareLineItem> items,
            BigDecimal totalAmount
    ) {
    }

    public record FareQuoteResponse(
            UUID fareQuoteId,
            String currency,
            String status,
            BigDecimal distanceKm,
            String trainNumber,
            String trainName,
            String originName,
            String destinationName,
            LocalDate journeyDate,
            String coachClassCode,
            String coachClassName,
            int passengerCount,
            List<PassengerFare> passengers,
            BigDecimal totalAmount,
            Instant createdAt,
            Instant expiresAt,
            int fareRuleVersionNumber,
            String quoteOnlyNotice
    ) {
    }

    public record FareRuleAuditEvent(
            UUID auditEventId,
            UUID fareRuleVersionId,
            String actorName,
            String eventType,
            String details,
            Instant occurredAt
    ) {
    }

    public record FareVersionDetail(
            FareVersionSummary version,
            FareRuleVersionRequest configuration
    ) {
    }

    public record FareAdminOptions(
            List<FareTrainOption> trains,
            List<FareLookupOption> trainCategories,
            List<FareLookupOption> coachClasses,
            List<FareLookupOption> stations
    ) {
    }

    public record FareTrainOption(UUID trainId, String trainNumber, String trainName, UUID categoryId) {
    }

    public record FareLookupOption(UUID id, String code, String name) {
    }

    public record FareSimulationRequest(
            @NotNull UUID fareRuleVersionId,
            @NotNull @Valid FareQuoteRequest journey
    ) {
    }
}
