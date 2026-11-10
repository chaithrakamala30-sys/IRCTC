package in.railconnect.core.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SupportModels {

    public enum TicketStatus {
        OPEN,
        ASSIGNED,
        IN_PROGRESS,
        WAITING_FOR_CUSTOMER,
        WAITING_FOR_INTERNAL_TEAM,
        ESCALATED,
        RESOLVED,
        CLOSED,
        REOPENED
    }

    public enum TicketPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT,
        CRITICAL
    }

    public record SupportCategoryView(
            UUID categoryId,
            String categoryCode,
            String displayName,
            String department,
            String defaultPriority,
            int slaResponseHours,
            int slaResolutionHours
    ) {}

    public record CreateTicketRequest(
            UUID categoryId,
            String subject,
            String description,
            String priority,
            UUID linkedBookingId,
            UUID linkedPaymentId,
            UUID linkedTicketId,
            UUID linkedTrainRunId
    ) {}

    public record SendMessageRequest(
            String messageBody,
            boolean isInternalNote
    ) {}

    public record UpdateTicketStatusRequest(
            TicketStatus newStatus,
            String reason
    ) {}

    public record AssignTicketRequest(
            UUID staffUserId,
            UUID teamId
    ) {}

    public record EscalateTicketRequest(
            int escalationLevel,
            String reason
    ) {}

    public record SupportFeedbackRequest(
            int satisfactionRating,
            String comments
    ) {}

    public record SupportMessageView(
            UUID messageId,
            UUID ticketId,
            UUID senderUserId,
            String senderName,
            String senderType,
            boolean isInternalNote,
            String messageBody,
            Instant createdAt
    ) {}

    public record SupportTicketSummaryView(
            UUID ticketId,
            String ticketReference,
            UUID passengerUserId,
            String passengerName,
            String categoryDisplayName,
            String subject,
            TicketPriority priority,
            TicketStatus status,
            String assignedStaffName,
            Instant slaTargetResolutionAt,
            boolean isSlaBreached,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SupportTicketDetailView(
            UUID ticketId,
            String ticketReference,
            UUID passengerUserId,
            String passengerName,
            String passengerEmail,
            UUID categoryId,
            String categoryDisplayName,
            String subject,
            String description,
            TicketPriority priority,
            TicketStatus status,
            UUID assignedStaffId,
            String assignedStaffName,
            UUID assignedTeamId,
            String assignedTeamName,
            UUID linkedBookingId,
            String linkedBookingReference,
            UUID linkedPaymentId,
            UUID linkedTicketId,
            String linkedPnr,
            UUID linkedTrainRunId,
            Instant slaTargetResponseAt,
            Instant slaTargetResolutionAt,
            boolean isSlaBreached,
            Instant resolvedAt,
            Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            List<SupportMessageView> messages
    ) {}

    public record SupportDashboardStatsView(
            long totalTickets,
            long openTickets,
            long unassignedTickets,
            long myAssignedTickets,
            long escalatedTickets,
            long resolvedTickets,
            long slaBreachedTickets,
            double averageSatisfactionScore
    ) {}
}
