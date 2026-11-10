package in.railconnect.core.support;

import in.railconnect.core.notification.NotificationService;
import in.railconnect.core.support.SupportModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private NotificationService notificationService;

    private SupportTicketService supportTicketService;

    @BeforeEach
    void setUp() {
        supportTicketService = new SupportTicketService(jdbcTemplate, notificationService);
    }

    @Test
    void testCreateTicket() {
        UUID passengerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CreateTicketRequest req = new CreateTicketRequest(
                categoryId, "Payment deducted", "Amount deducted but no PNR", "URGENT",
                null, null, null, null
        );

        when(jdbcTemplate.queryForMap(anyString(), eq(categoryId))).thenReturn(Map.of(
                "sla_response_hours", 2,
                "sla_resolution_hours", 12,
                "default_priority", "HIGH"
        ));

        UUID ticketId = supportTicketService.createTicket(passengerId, req);
        assertNotNull(ticketId);

        // Verify ticket inserted
        verify(jdbcTemplate, atLeastOnce()).update(contains("INSERT INTO support_tickets"), any(Object[].class));
        // Verify passenger notification triggered
        verify(notificationService, times(1)).createAndDispatchNotification(eq(passengerId), any(), anyString(), anyString(), anyString(), any(), anyString(), anyMap());
    }

    @Test
    void testAssignTicket() {
        UUID ticketId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID assignerId = UUID.randomUUID();

        supportTicketService.assignTicket(ticketId, staffId, assignerId);
        verify(jdbcTemplate, times(1)).update(contains("UPDATE support_tickets SET"), eq(staffId), eq(ticketId));
    }

    @Test
    void testEscalateTicket() {
        UUID ticketId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        supportTicketService.escalateTicket(ticketId, 2, "Senior intervention needed", staffId);
        verify(jdbcTemplate, times(1)).update(contains("UPDATE support_tickets SET"), eq(ticketId));
        verify(jdbcTemplate, times(1)).update(contains("INSERT INTO support_escalations"), any(), eq(ticketId), eq(2), eq(staffId), eq("Senior intervention needed"));
    }
}
