package in.railconnect.core.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentRateLimiter {
    private final JdbcTemplate jdbc;
    private final int maximumRequestsPerMinute;

    public PaymentRateLimiter(
            JdbcTemplate jdbc,
            @Value("${railconnect.pay.requests-per-minute:6}") int maximumRequestsPerMinute
    ) {
        if (maximumRequestsPerMinute < 1 || maximumRequestsPerMinute > 60) {
            throw new IllegalArgumentException("railconnect.pay.requests-per-minute must be between 1 and 60");
        }
        this.jdbc = jdbc;
        this.maximumRequestsPerMinute = maximumRequestsPerMinute;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean allow(UUID ownerId) {
        Integer requestCount = jdbc.queryForObject("""
                INSERT INTO payment_request_rate_limits AS stored(
                    owner_user_id, window_started_at, request_count
                ) VALUES (?, CURRENT_TIMESTAMP, 1)
                ON CONFLICT (owner_user_id) DO UPDATE
                SET window_started_at = CASE
                        WHEN CURRENT_TIMESTAMP >= stored.window_started_at + INTERVAL '1 minute'
                        THEN CURRENT_TIMESTAMP ELSE stored.window_started_at END,
                    request_count = CASE
                        WHEN CURRENT_TIMESTAMP >= stored.window_started_at + INTERVAL '1 minute'
                        THEN 1 ELSE stored.request_count + 1 END
                RETURNING request_count
                """, Integer.class, ownerId);
        return requestCount != null && requestCount <= maximumRequestsPerMinute;
    }
}
