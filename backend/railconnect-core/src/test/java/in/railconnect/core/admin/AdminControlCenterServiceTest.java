package in.railconnect.core.admin;

import in.railconnect.core.admin.AdminModels.*;
import in.railconnect.core.identity.RailConnectPrincipal;
import in.railconnect.core.identity.audit.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControlCenterServiceTest {

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private JdbcClient.StatementSpec statementSpec;

    @Mock
    private JdbcClient.MappedQuerySpec<Object> mappedQuerySpec;

    private AdminControlCenterService service;

    @BeforeEach
    void setUp() {
        service = new AdminControlCenterService(jdbcClient, auditService);
    }

    @Test
    void testSelfApprovalPrevention() {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        RailConnectPrincipal principal = new RailConnectPrincipal(userId, "test@railconnect.in", List.of("ROLE_SUPER_ADMIN"));

        ApprovalRequestRecord req = new ApprovalRequestRecord(
                requestId, "FARE_RULE_CHANGE", "Reduce 2A Fare", "Test", "{}", "PENDING_APPROVAL",
                userId, "Requester Name", null, null, null, null, null, null
        );

        when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
        when(statementSpec.param(eq("id"), eq(requestId))).thenReturn(statementSpec);
        when(statementSpec.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn(mappedQuerySpec);
        when(mappedQuerySpec.optional()).thenReturn(java.util.Optional.of(req));

        ReviewApprovalRequest review = new ReviewApprovalRequest(true, "Looks good");

        SecurityException ex = assertThrows(SecurityException.class, () ->
                service.reviewApprovalRequest(requestId, review, principal)
        );

        assertTrue(ex.getMessage().contains("Maker-checker violation"));
    }
}

