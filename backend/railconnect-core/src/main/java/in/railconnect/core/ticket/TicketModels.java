package in.railconnect.core.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class TicketModels {
    private TicketModels() {
    }

    public record TicketPassenger(
            String ticketPassengerId,
            int passengerNumber,
            String passengerName,
            String coachNumber,
            String berthNumber,
            String berthType,
            BigDecimal farePaid,
            String currency
    ) {
    }

    public record TicketView(
            UUID ticketId,
            String pnr,
            UUID bookingId,
            String bookingReference,
            String ticketStatus,
            Instant bookingCreatedAt,
            Instant issuedAt,
            String trainNumber,
            String trainName,
            String coachClassCode,
            LocalDate journeyDate,
            String boardingStation,
            String destinationStation,
            LocalTime boardingTime,
            LocalTime arrivalTime,
            BigDecimal totalPaid,
            String currency,
            String paymentReference,
            String paymentStatus,
            List<TicketPassenger> passengers
    ) {
    }

    public record TicketSummary(
            UUID ticketId,
            String pnr,
            String bookingReference,
            String ticketStatus,
            String trainNumber,
            String trainName,
            LocalDate journeyDate,
            String boardingStation,
            String destinationStation,
            int passengerCount,
            Instant issuedAt
    ) {
    }

    public record TicketPage(List<TicketSummary> items, int page, int size, long total) {
    }

    public record TicketVerificationRequest(
            @NotBlank
            @Pattern(regexp = "RC-TK-[A-Z2-7]{16}")
            String pnr
    ) {
    }

    public record TicketVerification(
            boolean valid,
            String pnr,
            String ticketStatus,
            String trainNumber,
            String trainName,
            LocalDate journeyDate,
            String boardingStation,
            String destinationStation,
            int passengerCount,
            Instant verifiedAt,
            String notice
    ) {
    }

    public record TicketAllocation(
            int passengerNumber,
            String coachNumber,
            String berthNumber,
            String berthType
    ) {
    }

    public record TicketOperationsVerification(
            TicketVerification verification,
            List<TicketAllocation> allocations
    ) {
    }

    public record TicketIssueEvent(String eventType, String reasonCode, Instant occurredAt) {
    }

    public record TicketDiagnostics(
            UUID ticketId,
            String pnr,
            String issuanceStatus,
            int attemptCount,
            String lastFailureCode,
            List<TicketIssueEvent> issueHistory
    ) {
    }

    public record TicketIssuanceFailure(
            String bookingReference,
            String trainNumber,
            java.time.LocalDate journeyDate,
            String jobStatus,
            int attemptCount,
            String lastFailureCode,
            Instant updatedAt
    ) {
    }
}
