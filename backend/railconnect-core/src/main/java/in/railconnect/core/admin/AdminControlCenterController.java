package in.railconnect.core.admin;

import in.railconnect.core.admin.AdminModels.*;
import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.CurrentUser;
import in.railconnect.core.identity.RailConnectPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminControlCenterController {

    private final AdminControlCenterService adminService;

    public AdminControlCenterController(AdminControlCenterService adminService) {
        this.adminService = adminService;
    }

    // 1. Executive Dashboard
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN', 'FINANCE_ADMIN')")
    public ApiEnvelope<ExecutiveDashboardMetrics> getDashboard() {
        return ApiEnvelope.success(adminService.getExecutiveDashboard(), RequestIds.current());
    }

    // 2. User & Passenger Management
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN', 'CUSTOMER_SUPPORT')")
    public ApiEnvelope<List<PassengerAccountSummary>> searchPassengers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status
    ) {
        return ApiEnvelope.success(adminService.searchPassengers(query, status), RequestIds.current());
    }

    @PostMapping("/users/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN')")
    public ApiEnvelope<String> updatePassengerStatus(
            @PathVariable UUID userId,
            @RequestBody PassengerStatusUpdateRequest request,
            @CurrentUser RailConnectPrincipal principal
    ) {
        adminService.updatePassengerStatus(userId, request, principal);
        return ApiEnvelope.success("Passenger status updated successfully", RequestIds.current());
    }

    // 3. Staff & Departments
    @GetMapping("/staff/departments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN')")
    public ApiEnvelope<List<StaffDepartmentRecord>> getDepartments() {
        return ApiEnvelope.success(adminService.getStaffDepartments(), RequestIds.current());
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN')")
    public ApiEnvelope<List<StaffProfileRecord>> listStaff() {
        return ApiEnvelope.success(adminService.listStaffProfiles(), RequestIds.current());
    }

    @PostMapping("/staff")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiEnvelope<StaffProfileRecord> createStaff(
            @RequestBody CreateStaffRequest request,
            @CurrentUser RailConnectPrincipal principal
    ) {
        return ApiEnvelope.success(adminService.createStaffAccount(request, principal), RequestIds.current());
    }

    // 4. Maker-Checker Approvals
    @GetMapping("/approvals")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN', 'FINANCE_ADMIN')")
    public ApiEnvelope<List<ApprovalRequestRecord>> listApprovals(
            @RequestParam(required = false) String status
    ) {
        return ApiEnvelope.success(adminService.listApprovalRequests(status), RequestIds.current());
    }

    @PostMapping("/approvals")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN', 'FINANCE_ADMIN', 'CUSTOMER_SUPPORT')")
    public ApiEnvelope<ApprovalRequestRecord> createApprovalRequest(
            @RequestBody CreateApprovalRequest request,
            @CurrentUser RailConnectPrincipal principal
    ) {
        return ApiEnvelope.success(adminService.createApprovalRequest(request, principal), RequestIds.current());
    }

    @PostMapping("/approvals/{requestId}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN', 'FINANCE_ADMIN')")
    public ApiEnvelope<String> reviewApproval(
            @PathVariable UUID requestId,
            @RequestBody ReviewApprovalRequest review,
            @CurrentUser RailConnectPrincipal principal
    ) {
        adminService.reviewApprovalRequest(requestId, review, principal);
        return ApiEnvelope.success("Approval review processed successfully", RequestIds.current());
    }

    // 5. Finance & Dhruva Bank Reconciliation
    @GetMapping("/finance/reconciliation")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FINANCE_ADMIN')")
    public ApiEnvelope<List<ReconciliationRunSummary>> listReconciliations() {
        return ApiEnvelope.success(adminService.listReconciliationRuns(), RequestIds.current());
    }

    @PostMapping("/finance/reconciliation/run")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'FINANCE_ADMIN')")
    public ApiEnvelope<ReconciliationRunSummary> runReconciliation(
            @CurrentUser RailConnectPrincipal principal
    ) {
        return ApiEnvelope.success(adminService.runReconciliation(principal), RequestIds.current());
    }

    // 6. Reports & CSV Export
    @GetMapping("/reports/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN', 'FINANCE_ADMIN')")
    public ResponseEntity<String> exportReport(
            @RequestParam(defaultValue = "BOOKINGS") String reportType
    ) {
        String csvData = adminService.generateCsvReport(reportType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=railconnect_" + reportType.toLowerCase() + "_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvData);
    }

    // 7. System Configurations
    @GetMapping("/configurations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RAILWAY_ADMIN')")
    public ApiEnvelope<List<SystemConfigRecord>> getConfigurations() {
        return ApiEnvelope.success(adminService.getSystemConfigurations(), RequestIds.current());
    }

    @PostMapping("/configurations/{key}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiEnvelope<String> updateConfiguration(
            @PathVariable String key,
            @RequestBody UpdateSystemConfigRequest request,
            @CurrentUser RailConnectPrincipal principal
    ) {
        adminService.updateSystemConfiguration(key, request, principal);
        return ApiEnvelope.success("Configuration updated successfully", RequestIds.current());
    }
}

