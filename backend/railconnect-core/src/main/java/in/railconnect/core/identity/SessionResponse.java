package in.railconnect.core.identity;

import java.time.Instant;

public record SessionResponse(
        String sessionId,
        Instant createdAt,
        Instant lastSeenAt,
        String userAgent,
        boolean current
) {
}
