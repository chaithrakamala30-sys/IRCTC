package in.railconnect.core.admin;

import in.railconnect.core.admin.AdminModels.*;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.identity.SecurityAuditService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AdminControlCenterService {

    private final JdbcClient jdbcClient;
    private final SecurityAuditService auditService;

    public AdminControlCenterService(JdbcClient jdbcClient, SecurityAuditService auditService) {
        this.jdbcClient = jdbcClient;
        this.auditService = auditService;
    }

    // =========================================================================
    // 1. Executive Dashboard Overview
    // =========================================================================
    @Transactional(readOnly = true)
    public ExecutiveDashboardMetrics getExecutiveDashboard() {
        // Platform overview
        long totalPassengers = queryCount("SELECT COUNT(*) FROM users u JOIN user_roles ur ON u.user_id = ur.user_id WHERE ur.role_code = 'PASSENGER'");
        long activePassengers = queryCount("SELECT COUNT(*) FROM users u JOIN user_roles ur ON u.user_id = ur.user_id WHERE ur.role_code = 'PASSENGER' AND u.status = 'ACTIVE'");
        long suspendedPassengers = queryCount("SELECT COUNT(*) FROM users u JOIN user_roles ur ON u.user_id = ur.user_id WHERE ur.role_code = 'PASSENGER' AND u.status = 'SUSPENDED'");
        long totalStaff = queryCount("SELECT COUNT(*) FROM staff_profiles WHERE is_active = TRUE");

        long totalBookings = queryCount("SELECT COUNT(*) FROM bookings");
        long confirmedBookings = queryCount("SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED'");
        long racBookings = queryCount("SELECT COUNT(*) FROM bookings WHERE status = 'RAC'");
        long waitlistBookings = queryCount("SELECT COUNT(*) FROM bookings WHERE status = 'WAITLISTED'");
        long cancelledBookings = queryCount("SELECT COUNT(*) FROM bookings WHERE status = 'CANCELLED'");

        PlatformOverview platform = new PlatformOverview(
                totalPassengers, activePassengers, suspendedPassengers, totalStaff,
                totalBookings, confirmedBookings, racBookings, waitlistBookings, cancelledBookings
        );

        // Financial overview (Demo Dhruva Bank transactions)
        BigDecimal totalPaymentVolume = querySum("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'SUCCESS'");
        long successfulPayments = queryCount("SELECT COUNT(*) FROM payments WHERE status = 'SUCCESS'");
        long failedPayments = queryCount("SELECT COUNT(*) FROM payments WHERE status = 'FAILED'");
        long pendingPayments = queryCount("SELECT COUNT(*) FROM payments WHERE status = 'PENDING'");

        BigDecimal totalRefundVolume = querySum("SELECT COALESCE(SUM(refund_amount), 0) FROM refund_requests WHERE status = 'COMPLETED'");
        long completedRefunds = queryCount("SELECT COUNT(*) FROM refund_requests WHERE status = 'COMPLETED'");
        long pendingRefunds = queryCount("SELECT COUNT(*) FROM refund_requests WHERE status = 'PENDING'");

        FinancialOverview finance = new FinancialOverview(
                totalPaymentVolume, successfulPayments, failedPayments, pendingPayments,
                totalRefundVolume, completedRefunds, pendingRefunds,
                "DEMO_GATEWAY_ONLINE", "RECONCILED"
        );

        // Service overview
        long activeTrainRuns = queryCount("SELECT COUNT(*) FROM train_runs WHERE run_date = CURRENT_DATE");
        long delayedTrainRuns = queryCount("SELECT COUNT(*) FROM train_runs WHERE run_date = CURRENT_DATE AND (status = 'DELAYED' OR current_delay_minutes > 0)");
        long cancelledTrainRuns = queryCount("SELECT COUNT(*) FROM train_runs WHERE run_date = CURRENT_DATE AND status = 'CANCELLED'");
        long activeDisruptions = queryCount("SELECT COUNT(*) FROM operational_disruptions WHERE resolved_at IS NULL");

        long openSupportTickets = queryCount("SELECT COUNT(*) FROM support_tickets WHERE status NOT IN ('RESOLVED', 'CLOSED')");
        long criticalSupportIncidents = queryCount("SELECT COUNT(*) FROM support_tickets WHERE priority = 'CRITICAL' AND status NOT IN ('RESOLVED', 'CLOSED')");

        ServiceOverview service = new ServiceOverview(
                activeTrainRuns, delayedTrainRuns, cancelledTrainRuns, activeDisruptions,
                openSupportTickets, criticalSupportIncidents
        );

        return new ExecutiveDashboardMetrics(platform, finance, service);
    }

    // =========================================================================
    // 2. User & Passenger Administration
    // =========================================================================
    @Transactional(readOnly = true)
    public List<PassengerAccountSummary> searchPassengers(String query, String status) {
        String sql = """
            SELECT u.user_id, u.full_name, u.email, u.mobile_number, u.status, u.created_at,
                   (SELECT COUNT(*) FROM bookings b WHERE b.user_id = u.user_id) as booking_count,
                   (SELECT COUNT(*) FROM support_tickets st WHERE st.customer_id = u.user_id) as ticket_count
            FROM users u
            JOIN user_roles ur ON u.user_id = ur.user_id
            WHERE ur.role_code = 'PASSENGER'
              AND (:query IS NULL OR :query = '' OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', :query, '%')) OR u.email LIKE CONCAT('%', :query, '%') OR u.mobile_number LIKE CONCAT('%', :query, '%'))
              AND (:status IS NULL OR :status = '' OR u.status = :status)
            ORDER BY u.created_at DESC
            LIMIT 50
        """;

        return jdbcClient.sql(sql)
                .param("query", query)
                .param("status", status)
                .query((rs, rowNum) -> new PassengerAccountSummary(
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("mobile_number"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getLong("booking_count"),
                        rs.getLong("ticket_count")
                )).list();
    }

    @Transactional
    public void updatePassengerStatus(UUID userId, PassengerStatusUpdateRequest request, RailConnectPrincipal principal) {
        if (!List.of("ACTIVE", "SUSPENDED", "DISABLED").contains(request.newStatus())) {
            throw new IllegalArgumentException("Invalid status: " + request.newStatus());
        }

        int updated = jdbcClient.sql("UPDATE users SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE user_id = :userId")
                .param("status", request.newStatus())
                .param("userId", userId)
                .update();

        if (updated > 0) {
            auditService.recordAdminAction(
                    principal.userId(),
                    "USER_STATUS_UPDATE",
                    "Target passenger: " + userId + " changed to " + request.newStatus() + ". Reason: " + request.reason()
            );
        }
    }

    // =========================================================================
    // 3. Staff & Department Administration
    // =========================================================================
    @Transactional(readOnly = true)
    public List<StaffDepartmentRecord> getStaffDepartments() {
        return jdbcClient.sql("SELECT department_code, name, description FROM staff_departments ORDER BY name ASC")
                .query((rs, rowNum) -> new StaffDepartmentRecord(
                        rs.getString("department_code"),
                        rs.getString("name"),
                        rs.getString("description")
                )).list();
    }

    @Transactional(readOnly = true)
    public List<StaffProfileRecord> listStaffProfiles() {
        String sql = """
            SELECT sp.staff_id, sp.user_id, sp.staff_code, u.full_name, u.email, u.mobile_number,
                   sp.department_code, sd.name as department_name, sp.designation, sp.is_active, sp.joined_at,
                   ARRAY_AGG(ur.role_code) as roles
            FROM staff_profiles sp
            JOIN users u ON sp.user_id = u.user_id
            JOIN staff_departments sd ON sp.department_code = sd.department_code
            LEFT JOIN user_roles ur ON u.user_id = ur.user_id
            GROUP BY sp.staff_id, sp.user_id, sp.staff_code, u.full_name, u.email, u.mobile_number,
                     sp.department_code, sd.name, sp.designation, sp.is_active, sp.joined_at
            ORDER BY sp.created_at DESC
        """;

        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> {
                    java.sql.Array rolesArr = rs.getArray("roles");
                    List<String> rolesList = rolesArr != null ? Arrays.asList((String[]) rolesArr.getArray()) : List.of();
                    return new StaffProfileRecord(
                            UUID.fromString(rs.getString("staff_id")),
                            UUID.fromString(rs.getString("user_id")),
                            rs.getString("staff_code"),
                            rs.getString("full_name"),
                            rs.getString("email"),
                            rs.getString("mobile_number"),
                            rs.getString("department_code"),
                            rs.getString("department_name"),
                            rs.getString("designation"),
                            rs.getBoolean("is_active"),
                            rs.getDate("joined_at").toLocalDate(),
                            rolesList
                    );
                }).list();
    }

    @Transactional
    public StaffProfileRecord createStaffAccount(CreateStaffRequest request, RailConnectPrincipal principal) {
        UUID newUserId = UUID.randomUUID();
        String staffCode = "STF-" + (1000 + (int)(Math.random() * 9000));

        // Create base user
        jdbcClient.sql("""
            INSERT INTO users (user_id, full_name, email, mobile_number, password_hash, status, terms_version, privacy_version)
            VALUES (:userId, :fullName, :email, :mobile, '$2a$10$temporaryhashforstaffaccounts0000000000000000000', 'ACTIVE', 'v1.0', 'v1.0')
        """)
        .param("userId", newUserId)
        .param("fullName", request.fullName())
        .param("email", request.email())
        .param("mobile", request.mobileNumber())
        .update();

        // Assign role
        jdbcClient.sql("""
            INSERT INTO user_roles (user_id, role_code, scope_type, scope_id, assigned_by)
            VALUES (:userId, :roleCode, 'GLOBAL', '*', :assignedBy)
        """)
        .param("userId", newUserId)
        .param("roleCode", request.roleCode())
        .param("assignedBy", principal.userId())
        .update();

        // Create staff profile
        UUID staffId = UUID.randomUUID();
        jdbcClient.sql("""
            INSERT INTO staff_profiles (staff_id, user_id, staff_code, department_code, designation, is_active)
            VALUES (:staffId, :userId, :staffCode, :deptCode, :designation, TRUE)
        """)
        .param("staffId", staffId)
        .param("userId", newUserId)
        .param("staffCode", staffCode)
        .param("deptCode", request.departmentCode())
        .param("designation", request.designation())
        .update();

        auditService.recordAdminAction(principal.userId(), "STAFF_CREATED", "Created staff: " + staffCode + " (" + request.fullName() + ")");

        return new StaffProfileRecord(
                staffId, newUserId, staffCode, request.fullName(), request.email(), request.mobileNumber(),
                request.departmentCode(), request.departmentCode(), request.designation(), true, LocalDate.now(), List.of(request.roleCode())
        );
    }

    // =========================================================================
    // 4. Maker-Checker Approval System
    // =========================================================================
    @Transactional(readOnly = true)
    public List<ApprovalRequestRecord> listApprovalRequests(String status) {
        String sql = """
            SELECT ar.*,
                   u_req.full_name as requester_name,
                   u_rev.full_name as reviewer_name
            FROM admin_approval_requests ar
            JOIN users u_req ON ar.requested_by = u_req.user_id
            LEFT JOIN users u_rev ON ar.reviewed_by = u_rev.user_id
            WHERE (:status IS NULL OR :status = '' OR ar.status = :status)
            ORDER BY ar.requested_at DESC
        """;

        return jdbcClient.sql(sql)
                .param("status", status)
                .query((rs, rowNum) -> new ApprovalRequestRecord(
                        UUID.fromString(rs.getString("request_id")),
                        rs.getString("request_type"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("payload_json"),
                        rs.getString("status"),
                        UUID.fromString(rs.getString("requested_by")),
                        rs.getString("requester_name"),
                        rs.getString("reviewed_by") != null ? UUID.fromString(rs.getString("reviewed_by")) : null,
                        rs.getString("reviewer_name"),
                        rs.getString("review_remarks"),
                        rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toInstant() : null,
                        rs.getTimestamp("applied_at") != null ? rs.getTimestamp("applied_at").toInstant() : null
                )).list();
    }

    @Transactional
    public ApprovalRequestRecord createApprovalRequest(CreateApprovalRequest request, RailConnectPrincipal principal) {
        UUID requestId = UUID.randomUUID();

        jdbcClient.sql("""
            INSERT INTO admin_approval_requests (request_id, request_type, title, description, payload_json, status, requested_by)
            VALUES (:requestId, :type, :title, :desc, CAST(:payload AS jsonb), 'PENDING_APPROVAL', :userId)
        """)
        .param("requestId", requestId)
        .param("type", request.requestType())
        .param("title", request.title())
        .param("desc", request.description())
        .param("payload", request.payloadJson() != null ? request.payloadJson() : "{}")
        .param("userId", principal.userId())
        .update();

        auditService.recordAdminAction(principal.userId(), "APPROVAL_REQUEST_SUBMITTED", "Submitted " + request.requestType() + ": " + request.title());

        return listApprovalRequests("PENDING_APPROVAL").stream()
                .filter(r -> r.requestId().equals(requestId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void reviewApprovalRequest(UUID requestId, ReviewApprovalRequest review, RailConnectPrincipal principal) {
        ApprovalRequestRecord req = jdbcClient.sql("SELECT * FROM admin_approval_requests WHERE request_id = :id")
                .param("id", requestId)
                .query((rs, rn) -> new ApprovalRequestRecord(
                        UUID.fromString(rs.getString("request_id")),
                        rs.getString("request_type"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("payload_json"),
                        rs.getString("status"),
                        UUID.fromString(rs.getString("requested_by")),
                        "", null, "", "", Instant.now(), null, null
                )).optional().orElseThrow(() -> new NoSuchElementException("Approval request not found"));

        if (!"PENDING_APPROVAL".equals(req.status())) {
            throw new IllegalStateException("Request is not pending approval. Current status: " + req.status());
        }

        // Strict enforcement: Maker cannot be Checker!
        if (req.requestedBy().equals(principal.userId())) {
            throw new SecurityException("Maker-checker violation: You cannot approve your own change request.");
        }

        String newStatus = review.approved() ? "APPROVED" : "REJECTED";

        jdbcClient.sql("""
            UPDATE admin_approval_requests
            SET status = :status,
                reviewed_by = :reviewerId,
                review_remarks = :remarks,
                reviewed_at = CURRENT_TIMESTAMP,
                applied_at = CASE WHEN :approved THEN CURRENT_TIMESTAMP ELSE NULL END
            WHERE request_id = :id
        """)
        .param("status", newStatus)
        .param("reviewerId", principal.userId())
        .param("remarks", review.reviewRemarks())
        .param("approved", review.approved())
        .param("id", requestId)
        .update();

        auditService.recordAdminAction(
                principal.userId(),
                "APPROVAL_REVIEWED",
                "Request " + requestId + " (" + req.title() + ") set to " + newStatus + ". Remarks: " + review.reviewRemarks()
        );
    }

    // =========================================================================
    // 5. Finance & Dhruva Bank Reconciliation
    // =========================================================================
    @Transactional(readOnly = true)
    public List<ReconciliationRunSummary> listReconciliationRuns() {
        String sql = """
            SELECT fr.*, u.full_name as initiator_name
            FROM finance_reconciliation_runs fr
            JOIN users u ON fr.initiated_by = u.user_id
            ORDER BY fr.run_date DESC, fr.created_at DESC
        """;

        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> new ReconciliationRunSummary(
                        UUID.fromString(rs.getString("run_id")),
                        rs.getString("run_code"),
                        rs.getDate("run_date").toLocalDate(),
                        rs.getInt("total_transactions"),
                        rs.getInt("matched_count"),
                        rs.getInt("discrepancy_count"),
                        rs.getBigDecimal("total_volume_inr"),
                        rs.getString("status"),
                        UUID.fromString(rs.getString("initiated_by")),
                        rs.getString("initiator_name"),
                        rs.getTimestamp("created_at").toInstant()
                )).list();
    }

    @Transactional
    public ReconciliationRunSummary runReconciliation(RailConnectPrincipal principal) {
        UUID runId = UUID.randomUUID();
        String runCode = "REC-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + (100 + (int)(Math.random() * 900));

        // Pull payment volume
        BigDecimal volume = querySum("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'SUCCESS'");
        int paymentsCount = (int) queryCount("SELECT COUNT(*) FROM payments");

        // Insert run header
        jdbcClient.sql("""
            INSERT INTO finance_reconciliation_runs (run_id, run_code, run_date, total_transactions, matched_count, discrepancy_count, total_volume_inr, status, initiated_by, notes)
            VALUES (:runId, :runCode, CURRENT_DATE, :total, :total, 0, :volume, 'COMPLETED', :userId, 'Automated reconciliation against Dhruva Bank demonstration ledger')
        """)
        .param("runId", runId)
        .param("runCode", runCode)
        .param("total", paymentsCount)
        .param("volume", volume)
        .param("userId", principal.userId())
        .update();

        auditService.recordAdminAction(principal.userId(), "FINANCE_RECONCILIATION_RUN", "Initiated run " + runCode);

        return listReconciliationRuns().stream().filter(r -> r.runId().equals(runId)).findFirst().orElseThrow();
    }

    // =========================================================================
    // 6. Reports & CSV Export
    // =========================================================================
    @Transactional(readOnly = true)
    public String generateCsvReport(String reportType) {
        StringBuilder csv = new StringBuilder();

        switch (reportType.toUpperCase()) {
            case "BOOKINGS" -> {
                csv.append("Booking ID,PNR,Status,Total Fare (INR),Created At\n");
                jdbcClient.sql("SELECT booking_id, pnr_number, status, total_fare, created_at FROM bookings ORDER BY created_at DESC LIMIT 500")
                        .query((rs, rowNum) -> {
                            csv.append(rs.getString("booking_id")).append(",")
                               .append(rs.getString("pnr_number")).append(",")
                               .append(rs.getString("status")).append(",")
                               .append(rs.getBigDecimal("total_fare")).append(",")
                               .append(rs.getTimestamp("created_at")).append("\n");
                            return null;
                        });
            }
            case "FINANCE" -> {
                csv.append("Payment ID,Booking ID,Amount (INR),Payment Status,Gateway,Created At\n");
                jdbcClient.sql("SELECT payment_id, booking_id, amount, status, gateway_name, created_at FROM payments ORDER BY created_at DESC LIMIT 500")
                        .query((rs, rowNum) -> {
                            csv.append(rs.getString("payment_id")).append(",")
                               .append(rs.getString("booking_id")).append(",")
                               .append(rs.getBigDecimal("amount")).append(",")
                               .append(rs.getString("status")).append(",")
                               .append(rs.getString("gateway_name")).append(",")
                               .append(rs.getTimestamp("created_at")).append("\n");
                            return null;
                        });
            }
            case "SUPPORT" -> {
                csv.append("Ticket Number,Category,Priority,Status,SLA Breached,Created At\n");
                jdbcClient.sql("SELECT ticket_number, category_code, priority, status, sla_breached, created_at FROM support_tickets ORDER BY created_at DESC LIMIT 500")
                        .query((rs, rowNum) -> {
                            csv.append(rs.getString("ticket_number")).append(",")
                               .append(rs.getString("category_code")).append(",")
                               .append(rs.getString("priority")).append(",")
                               .append(rs.getString("status")).append(",")
                               .append(rs.getBoolean("sla_breached")).append(",")
                               .append(rs.getTimestamp("created_at")).append("\n");
                            return null;
                        });
            }
            default -> {
                csv.append("User ID,Full Name,Email,Mobile,Status,Created At\n");
                jdbcClient.sql("SELECT user_id, full_name, email, mobile_number, status, created_at FROM users ORDER BY created_at DESC LIMIT 500")
                        .query((rs, rowNum) -> {
                            csv.append(rs.getString("user_id")).append(",")
                               .append(rs.getString("full_name")).append(",")
                               .append(rs.getString("email")).append(",")
                               .append(rs.getString("mobile_number")).append(",")
                               .append(rs.getString("status")).append(",")
                               .append(rs.getTimestamp("created_at")).append("\n");
                            return null;
                        });
            }
        }

        return csv.toString();
    }

    // =========================================================================
    // 7. System Configurations
    // =========================================================================
    @Transactional(readOnly = true)
    public List<SystemConfigRecord> getSystemConfigurations() {
        return jdbcClient.sql("SELECT config_key, config_value, description, is_protected, updated_at FROM system_configurations ORDER BY config_key ASC")
                .query((rs, rowNum) -> new SystemConfigRecord(
                        rs.getString("config_key"),
                        rs.getString("config_value"),
                        rs.getString("description"),
                        rs.getBoolean("is_protected"),
                        rs.getTimestamp("updated_at").toInstant()
                )).list();
    }

    @Transactional
    public void updateSystemConfiguration(String key, UpdateSystemConfigRequest request, RailConnectPrincipal principal) {
        int updated = jdbcClient.sql("""
            UPDATE system_configurations
            SET config_value = :val,
                updated_by = :userId,
                updated_at = CURRENT_TIMESTAMP
            WHERE config_key = :key
        """)
        .param("val", request.configValue())
        .param("userId", principal.userId())
        .param("key", key)
        .update();

        if (updated > 0) {
            auditService.recordAdminAction(
                    principal.userId(),
                    "SYSTEM_CONFIG_UPDATED",
                    "Config [" + key + "] updated to " + request.configValue() + ". Reason: " + request.reason()
            );
        }
    }

    // Helper queries
    private long queryCount(String sql) {
        try {
            Long count = jdbcClient.sql(sql).query(Long.class).single();
            return count != null ? count : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private BigDecimal querySum(String sql) {
        try {
            BigDecimal sum = jdbcClient.sql(sql).query(BigDecimal.class).single();
            return sum != null ? sum : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

