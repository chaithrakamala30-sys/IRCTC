package in.railconnect.core.identity;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditResponse(
        UUID auditId,
        UUID actorUserId,
        UUID targetUserId,
        String eventType,
        String outcome,
        String correlationId,
        Instant occurredAt
) {
}
