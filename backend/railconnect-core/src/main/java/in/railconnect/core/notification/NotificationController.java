package in.railconnect.core.notification;

import in.railconnect.core.api.ApiEnvelope;
import in.railconnect.core.api.RequestIds;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.notification.NotificationModels.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private static <T> ApiEnvelope<T> success(T data) {
        return ApiEnvelope.success(data, RequestIds.current());
    }

    // ==========================================
    // PASSENGER NOTIFICATION ENDPOINTS
    // ==========================================

    @GetMapping("/notifications")
    public ResponseEntity<ApiEnvelope<List<InAppNotificationView>>> getNotifications(
            @AuthenticationPrincipal RailConnectPrincipal principal,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<InAppNotificationView> list = notificationService.getUserNotifications(userId, category, unreadOnly, limit, offset);
        return ResponseEntity.ok(success(list));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiEnvelope<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(success(Map.of("unreadCount", count)));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        boolean updated = notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(success(Map.of("success", updated)));
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<ApiEnvelope<Map<String, Integer>>> markAllAsRead(
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(success(Map.of("updatedCount", count)));
    }

    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> deleteNotification(
            @PathVariable UUID id,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        boolean deleted = notificationService.deleteNotification(id, userId);
        return ResponseEntity.ok(success(Map.of("deleted", deleted)));
    }

    @GetMapping("/notifications/preferences")
    public ResponseEntity<ApiEnvelope<NotificationPreferenceView>> getPreferences(
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(success(notificationService.getPreferences(userId)));
    }

    @PutMapping("/notifications/preferences")
    public ResponseEntity<ApiEnvelope<NotificationPreferenceView>> updatePreferences(
            @RequestBody UpdatePreferencesRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(success(notificationService.updatePreferences(userId, request)));
    }

    @PostMapping("/notifications/devices")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> registerDevice(
            @RequestBody DeviceRegistrationRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        notificationService.registerDevice(userId, request);
        return ResponseEntity.ok(success(Map.of("status", "REGISTERED")));
    }

    @DeleteMapping("/notifications/devices/{token}")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> deregisterDevice(
            @PathVariable String token,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        boolean removed = notificationService.deregisterDevice(userId, token);
        return ResponseEntity.ok(success(Map.of("removed", removed)));
    }

    @PostMapping("/notifications/test")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> sendTestNotification(
            @RequestBody SendTestNotificationRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        UUID userId = principal != null ? principal.userId() : UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID notifId = notificationService.createAndDispatchNotification(
                userId,
                request.category() != null ? request.category() : NotificationCategory.SYSTEM,
                "TEST_NOTIFICATION",
                request.title() != null ? request.title() : "RailConnect Test Alert",
                request.message() != null ? request.message() : "This is a verified test notification from the RailConnect notification engine.",
                NotificationPriority.NORMAL,
                "/notifications",
                Map.of("source", "test_action")
        );
        return ResponseEntity.ok(success(Map.of("notificationId", (Object) notifId, "status", "DISPATCHED")));
    }

    // ==========================================
    // ADMIN ENDPOINTS
    // ==========================================

    @GetMapping("/admin/notifications/statistics")
    public ResponseEntity<ApiEnvelope<DeliveryStatsView>> getStats() {
        return ResponseEntity.ok(success(notificationService.getDeliveryStats()));
    }

    @GetMapping("/admin/notifications/templates")
    public ResponseEntity<ApiEnvelope<List<NotificationTemplateView>>> getTemplates() {
        return ResponseEntity.ok(success(notificationService.getTemplates()));
    }

    // ==========================================
    // OPERATIONS ENDPOINTS
    // ==========================================

    @PostMapping("/operations/announcements")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> publishAnnouncement(
            @RequestBody OperationalAnnouncementRequest request,
            @AuthenticationPrincipal RailConnectPrincipal principal
    ) {
        String publishedBy = principal != null ? principal.email() : "OPERATIONS_OFFICER";
        UUID announcementId = notificationService.publishOperationalAnnouncement(request, publishedBy);
        return ResponseEntity.ok(success(Map.of("announcementId", (Object) announcementId, "status", "PUBLISHED")));
    }
}
