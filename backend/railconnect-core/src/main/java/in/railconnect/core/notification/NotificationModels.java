package in.railconnect.core.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class NotificationModels {

    public enum NotificationCategory {
        BOOKING,
        PAYMENT,
        TICKET,
        PNR,
        CANCELLATION,
        REFUND,
        RESERVATION,
        WAITLIST,
        RAC,
        TRAIN_OPERATION,
        PLATFORM_CHANGE,
        ANNOUNCEMENT,
        ACCOUNT,
        SYSTEM
    }

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum DeliveryChannel {
        IN_APP,
        WEB_PUSH,
        FCM,
        EMAIL,
        SMS
    }

    public enum DeliveryStatus {
        PENDING,
        PROCESSING,
        SENT,
        DELIVERED,
        FAILED,
        RETRYING,
        EXPIRED
    }

    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        SENT,
        FAILED,
        DEAD_LETTER
    }

    public record InAppNotificationView(
            UUID id,
            UUID userId,
            NotificationCategory category,
            String eventType,
            String title,
            String message,
            NotificationPriority priority,
            boolean isRead,
            Instant readAt,
            String deepLink,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant expiresAt
    ) {}

    public record NotificationPreferenceView(
            UUID userId,
            boolean inAppEnabled,
            boolean pushEnabled,
            boolean emailEnabled,
            boolean bookingAlerts,
            boolean paymentAlerts,
            boolean ticketAlerts,
            boolean reservationAlerts,
            boolean operationalAlerts,
            boolean announcementAlerts,
            Instant updatedAt
    ) {}

    public record UpdatePreferencesRequest(
            Boolean inAppEnabled,
            Boolean pushEnabled,
            Boolean emailEnabled,
            Boolean bookingAlerts,
            Boolean paymentAlerts,
            Boolean ticketAlerts,
            Boolean reservationAlerts,
            Boolean operationalAlerts,
            Boolean announcementAlerts
    ) {}

    public record DeviceRegistrationRequest(
            String deviceToken,
            String platform,
            Map<String, Object> metadata
    ) {}

    public record SendTestNotificationRequest(
            NotificationCategory category,
            String title,
            String message
    ) {}

    public record NotificationTemplateView(
            UUID id,
            String templateCode,
            String category,
            String eventType,
            String titleTemplate,
            String bodyTemplate,
            String priority,
            String defaultDeepLink,
            boolean isActive
    ) {}

    public record OperationalAnnouncementRequest(
            String title,
            String message,
            String priority,
            String targetScope,
            UUID targetTrainRunId,
            String targetStationCode,
            Integer expiryHours
    ) {}

    public record DeliveryStatsView(
            long totalInApp,
            long unreadInApp,
            long totalOutbox,
            long pendingOutbox,
            long failedOutbox,
            long totalDeliveries,
            long sentDeliveries,
            long failedDeliveries
    ) {}
}
