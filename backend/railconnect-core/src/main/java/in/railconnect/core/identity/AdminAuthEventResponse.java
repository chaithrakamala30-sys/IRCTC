package in.railconnect.core.identity;

import java.time.Instant;
import java.util.UUID;

public record AdminAuthEventResponse(
        UUID eventId,
        UUID userId,
        String eventType,
        String outcome,
        String correlationId,
        Instant occurredAt
) {
}
