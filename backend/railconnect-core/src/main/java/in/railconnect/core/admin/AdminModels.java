package in.railconnect.core.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AdminModels {
    private AdminModels() {}

    // Executive Dashboard
    public record ExecutiveDashboardMetrics(
            PlatformOverview platform,
            FinancialOverview finance,
            ServiceOverview service
    ) {}

    public record PlatformOverview(
            long totalPassengers,
            long activePassengers,
            long suspendedPassengers,
            long totalStaff,
            long totalBookings,
            long confirmedBookings,
            long racBookings,
            long waitlistBookings,
            long cancelledBookings
    ) {}

    public record FinancialOverview(
            BigDecimal totalPaymentVolumeInr,
            long successfulPayments,
            long failedPayments,
            long pendingPayments,
            BigDecimal totalRefundVolumeInr,
            long completedRefunds,
            long pendingRefunds,
            String gatewayStatus,
            String reconciliationStatus
    ) {}

    public record ServiceOverview(
            long activeTrainRuns,
            long delayedTrainRuns,
            long cancelledTrainRuns,
            long activeDisruptions,
            long openSupportTickets,
            long criticalSupportIncidents
    ) {}

    // User Administration
    public record PassengerAccountSummary(
            UUID userId,
            String fullName,
            String email,
            String mobileNumber,
            String status,
            Instant createdAt,
            long bookingCount,
            long supportTicketCount
    ) {}

    public record PassengerStatusUpdateRequest(
            String newStatus,
            String reason
    ) {}

    // Staff Administration
    public record StaffDepartmentRecord(
            String departmentCode,
            String name,
            String description
    ) {}

    public record StaffProfileRecord(
            UUID staffId,
            UUID userId,
            String staffCode,
            String fullName,
            String email,
            String mobileNumber,
            String departmentCode,
            String departmentName,
            String designation,
            boolean isActive,
            LocalDate joinedAt,
            List<String> roles
    ) {}

    public record CreateStaffRequest(
            String fullName,
            String email,
            String mobileNumber,
            String departmentCode,
            String designation,
            String roleCode
    ) {}

    // Maker-Checker Approvals
    public record ApprovalRequestRecord(
            UUID requestId,
            String requestType,
            String title,
            String description,
            String payloadJson,
            String status,
            UUID requestedBy,
            String requesterName,
            UUID reviewedBy,
            String reviewerName,
            String reviewRemarks,
            Instant requestedAt,
            Instant reviewedAt,
            Instant appliedAt
    ) {}

    public record CreateApprovalRequest(
            String requestType,
            String title,
            String description,
            String payloadJson
    ) {}

    public record ReviewApprovalRequest(
            boolean approved,
            String reviewRemarks
    ) {}

    // Finance & Reconciliation
    public record ReconciliationRunSummary(
            UUID runId,
            String runCode,
            LocalDate runDate,
            int totalTransactions,
            int matchedCount,
            int discrepancyCount,
            BigDecimal totalVolumeInr,
            String status,
            UUID initiatedBy,
            String initiatorName,
            Instant createdAt
    ) {}

    public record ReconciliationItemRecord(
            UUID itemId,
            UUID runId,
            String referenceType,
            String internalReference,
            String gatewayReference,
            BigDecimal amountInr,
            String status,
            String remarks,
            Instant createdAt
    ) {}

    // Reports
    public record ReportQuery(
            String reportType, // PASSENGERS, BOOKINGS, FINANCE, OPERATIONS, SUPPORT
            LocalDate startDate,
            LocalDate endDate
    ) {}

    // System Configurations
    public record SystemConfigRecord(
            String configKey,
            String configValue,
            String description,
            boolean isProtected,
            Instant updatedAt
    ) {}

    public record UpdateSystemConfigRequest(
            String configValue,
            String reason
    ) {}
}

