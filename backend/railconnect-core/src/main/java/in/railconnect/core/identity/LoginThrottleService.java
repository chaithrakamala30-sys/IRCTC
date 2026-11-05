package in.railconnect.core.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class LoginThrottleService {
    private static final int MAX_FAILURES = 5;
    private static final int WINDOW_MINUTES = 15;
    private final JdbcTemplate jdbcTemplate;

    public LoginThrottleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isBlocked(String identifierHash) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM login_throttles
                            WHERE identifier_hash = ? AND blocked_until > CURRENT_TIMESTAMP
                        )
                        """,
                Boolean.class,
                identifierHash
        ));
    }

    public void recordFailure(String identifierHash) {
        jdbcTemplate.update(
                """
                        INSERT INTO login_throttles (
                            identifier_hash, failure_count, window_started_at, blocked_until, updated_at
                        ) VALUES (?, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP)
                        ON CONFLICT (identifier_hash) DO UPDATE SET
                            failure_count = CASE
                                WHEN login_throttles.window_started_at
                                    <= CURRENT_TIMESTAMP - (? * INTERVAL '1 minute') THEN 1
                                ELSE login_throttles.failure_count + 1
                            END,
                            window_started_at = CASE
                                WHEN login_throttles.window_started_at
                                    <= CURRENT_TIMESTAMP - (? * INTERVAL '1 minute')
                                    THEN CURRENT_TIMESTAMP
                                ELSE login_throttles.window_started_at
                            END,
                            blocked_until = CASE
                                WHEN CASE
                                    WHEN login_throttles.window_started_at
                                        <= CURRENT_TIMESTAMP - (? * INTERVAL '1 minute') THEN 1
                                    ELSE login_throttles.failure_count + 1
                                END >= ? THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 minute')
                                ELSE NULL
                            END,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                identifierHash,
                WINDOW_MINUTES,
                WINDOW_MINUTES,
                WINDOW_MINUTES,
                MAX_FAILURES,
                WINDOW_MINUTES
        );
    }

    public void clear(String identifierHash) {
        jdbcTemplate.update("DELETE FROM login_throttles WHERE identifier_hash = ?", identifierHash);
    }

    public Instant getBlockedUntil(String identifierHash) {
        return jdbcTemplate.query(
                "SELECT blocked_until FROM login_throttles WHERE identifier_hash = ?",
                result -> result.next() ? result.getTimestamp(1).toInstant() : null,
                identifierHash
        );
    }
}
