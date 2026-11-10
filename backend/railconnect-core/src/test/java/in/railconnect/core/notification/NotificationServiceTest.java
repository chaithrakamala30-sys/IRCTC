package in.railconnect.core.notification;

import in.railconnect.core.notification.NotificationModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ObjectMapper objectMapper;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        notificationService = new NotificationService(jdbcTemplate, objectMapper);
    }

    @Test
    void testGetUnreadCount() {
        UUID userId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(userId))).thenReturn(5L);

        long count = notificationService.getUnreadCount(userId);
        assertEquals(5L, count);
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Long.class), eq(userId));
    }

    @Test
    void testMarkAsRead() {
        UUID notificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(notificationId), eq(userId))).thenReturn(1);

        boolean result = notificationService.markAsRead(notificationId, userId);
        assertTrue(result);
    }

    @Test
    void testMarkAllAsRead() {
        UUID userId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(userId))).thenReturn(3);

        int updated = notificationService.markAllAsRead(userId);
        assertEquals(3, updated);
    }

    @Test
    void testQueueNotificationEventOutbox() {
        UUID userId = UUID.randomUUID();
        String idempotencyKey = "TEST-EVT-100";
        String eventType = "BOOKING_CONFIRMED";
        Map<String, Object> payload = Map.of("bookingReference", "RC-BK-999");

        notificationService.queueNotificationEvent(idempotencyKey, eventType, userId, payload);
        verify(jdbcTemplate, times(1)).update(anyString(), eq(idempotencyKey), eq(eventType), eq(userId), anyString());
    }
}
