package in.railconnect.core.support;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.support.SupportModels.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final SupportTicketService supportTicketService;

    public SupportController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }

    // ==========================================
    // PASSENGER SUPPORT APIS
    // ==========================================

    @GetMapping("/categories")
    public ResponseEntity<ApiEnvelope<List<SupportCategoryView>>> getCategories() {
        return ResponseEntity.ok(success(supportTicketService.getCategories()));
    }

    @PostMapping("/tickets")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createTicket(
            @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID passengerId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ticketId = supportTicketService.createTicket(passengerId, request);
        return ResponseEntity.ok(success(Map.of("ticketId", ticketId, "status", "CREATED")));
    }

    @GetMapping("/tickets")
    public ResponseEntity<ApiEnvelope<List<SupportTicketSummaryView>>> getMyTickets(
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID passengerId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(success(supportTicketService.getPassengerTickets(passengerId)));
    }

    @GetMapping("/tickets/{ticketId}")
    public ResponseEntity<ApiEnvelope<SupportTicketDetailView>> getTicketDetails(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID requesterId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        boolean isStaff = principal != null && principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().startsWith("ROLE_STAFF") || a.getAuthority().contains("support:"));
        return ResponseEntity.ok(success(supportTicketService.getTicketDetails(ticketId, requesterId, isStaff)));
    }

    @PostMapping("/tickets/{ticketId}/messages")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> sendMessage(
            @PathVariable UUID ticketId,
            @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID senderId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        boolean isStaff = principal != null && principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().startsWith("ROLE_STAFF") || a.getAuthority().contains("support:"));

        String senderType = isStaff ? "STAFF" : "PASSENGER";
        boolean isInternal = isStaff && request.isInternalNote();

        supportTicketService.addMessage(ticketId, senderId, senderType, request.messageBody(), isInternal);
        return ResponseEntity.ok(success(Map.of("status", "SENT")));
    }

    @PostMapping("/tickets/{ticketId}/feedback")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> submitFeedback(
            @PathVariable UUID ticketId,
            @RequestBody SupportFeedbackRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID passengerId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        supportTicketService.submitFeedback(ticketId, passengerId, request);
        return ResponseEntity.ok(success(Map.of("status", "SUBMITTED")));
    }

    // ==========================================
    // SUPPORT STAFF & ADMIN APIS
    // ==========================================

    @GetMapping("/staff/queue")
    public ResponseEntity<ApiEnvelope<List<SupportTicketSummaryView>>> getStaffQueue(
            @RequestParam(defaultValue = "ALL") String filter,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(success(supportTicketService.getStaffQueue(filter, staffId)));
    }

    @PostMapping("/staff/tickets/{ticketId}/assign")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> assignTicket(
            @PathVariable UUID ticketId,
            @RequestBody AssignTicketRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID assignerId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID targetStaffId = request.staffUserId() != null ? request.staffUserId() : assignerId;
        supportTicketService.assignTicket(ticketId, targetStaffId, assignerId);
        return ResponseEntity.ok(success(Map.of("status", "ASSIGNED")));
    }

    @PatchMapping("/staff/tickets/{ticketId}/status")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> updateStatus(
            @PathVariable UUID ticketId,
            @RequestBody UpdateTicketStatusRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        supportTicketService.updateTicketStatus(ticketId, request.newStatus(), request.reason(), staffId);
        return ResponseEntity.ok(success(Map.of("status", request.newStatus().name())));
    }

    @PostMapping("/staff/tickets/{ticketId}/escalate")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> escalateTicket(
            @PathVariable UUID ticketId,
            @RequestBody EscalateTicketRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        supportTicketService.escalateTicket(ticketId, request.escalationLevel(), request.reason(), staffId);
        return ResponseEntity.ok(success(Map.of("status", "ESCALATED")));
    }

    @GetMapping("/admin/dashboard")
    public ResponseEntity<ApiEnvelope<SupportDashboardStatsView>> getDashboardStats(
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID staffId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(success(supportTicketService.getDashboardStats(staffId)));
    }
}
