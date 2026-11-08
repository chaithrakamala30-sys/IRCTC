package in.railconnect.core.booking;

import in.railconnect.core.fare.FareModels.FareLineItem;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BookingModels {
    private BookingModels() {
    }

    public record CreateBookingRequest(
            @NotNull UUID fareQuoteId,
            @NotNull UUID reservationRequestId
    ) {
    }

    public record BookingPassenger(
            int passengerNumber,
            String displayName,
            String coachNumber,
            String berthNumber,
            String berthType,
            String passengerCategory,
            List<FareLineItem> fareItems,
            BigDecimal totalFare
    ) {
    }

    public record BookingStateChange(
            String previousStatus,
            String newStatus,
            String reasonCode,
            Instant occurredAt
    ) {
    }

    public record BookingStatus(
            UUID bookingId,
            String bookingReference,
            String status,
            Instant paymentDeadline,
            List<BookingStateChange> history
    ) {
    }

    public record BookingFare(
            UUID fareQuoteId,
            int fareRuleVersionNumber,
            BigDecimal distanceKm,
            BigDecimal totalPayable,
            String currency,
            List<BookingPassenger> passengers
    ) {
    }

    public record BookingDetails(
            UUID bookingId,
            String bookingReference,
            String status,
            String trainNumber,
            String trainName,
            UUID trainRunId,
            LocalDate journeyDate,
            UUID fromStationId,
            String originName,
            UUID toStationId,
            String destinationName,
            String coachClassCode,
            BigDecimal distanceKm,
            String groupSeatingResult,
            UUID fareQuoteId,
            int fareRuleVersionNumber,
            BigDecimal totalPayable,
            String currency,
            Instant paymentDeadline,
            Instant createdAt,
            List<BookingPassenger> passengers,
            List<BookingStateChange> stateHistory
    ) {
    }

    public record BookingSummary(
            UUID bookingId,
            String bookingReference,
            String status,
            String trainNumber,
            String trainName,
            LocalDate journeyDate,
            String originName,
            String destinationName,
            String coachClassCode,
            int passengerCount,
            BigDecimal totalPayable,
            String currency,
            Instant paymentDeadline,
            Instant createdAt
    ) {
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
    }
}
