package in.railconnect.core.identity;

import in.railconnect.core.api.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SecurityAuditService {
    private final JdbcTemplate jdbcTemplate;
    private final SecurityHashing hashing;

    public SecurityAuditService(JdbcTemplate jdbcTemplate, SecurityHashing hashing) {
        this.jdbcTemplate = jdbcTemplate;
        this.hashing = hashing;
    }

    @Transactional
    public void record(
            String eventType,
            String outcome,
            UUID actorUserId,
            UUID targetUserId,
            String identifierHash,
            HttpServletRequest request
    ) {
        String remoteAddressHash = request == null
                ? null
                : hashing.hash("remote-address:" + request.getRemoteAddr());
        String correlationId = RequestIds.current();
        jdbcTemplate.update(
                """
                        INSERT INTO security_audit_logs (
                            audit_id, actor_user_id, target_user_id, event_type, outcome,
                            correlation_id, metadata
                        ) VALUES (?, ?, ?, ?, ?, ?, jsonb_build_object('remoteAddressHash', ?))
                        """,
                UUID.randomUUID(),
                actorUserId,
                targetUserId,
                eventType,
                "SUCCESS".equals(outcome) ? "SUCCESS" : "FAILURE",
                correlationId,
                remoteAddressHash
        );
        if (eventType.startsWith("AUTH_")) {
            jdbcTemplate.update(
                    """
                            INSERT INTO authentication_events (
                                event_id, user_id, identifier_hash, event_type, outcome,
                                correlation_id, remote_address_hash
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    UUID.randomUUID(),
                    targetUserId,
                    identifierHash,
                    eventType,
                    outcome,
                    correlationId,
                    remoteAddressHash
            );
        }
    }

    @Transactional
    public void recordAdminAction(UUID actorUserId, String actionType, String details) {
        String correlationId = RequestIds.current();
        jdbcTemplate.update(
                """
                        INSERT INTO security_audit_logs (
                            audit_id, actor_user_id, target_user_id, event_type, outcome,
                            correlation_id, metadata
                        ) VALUES (?, ?, ?, ?, ?, ?, jsonb_build_object('details', ?::text))
                        """,
                UUID.randomUUID(),
                actorUserId,
                null,
                actionType,
                "SUCCESS",
                correlationId,
                details
        );
    }
}
