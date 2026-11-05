package in.railconnect.core.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RegistrationThrottleService {
    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 15;
    private final JdbcTemplate jdbcTemplate;

    public RegistrationThrottleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isBlocked(String remoteAddressHash) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1 FROM registration_throttles
                            WHERE remote_address_hash = ? AND blocked_until > CURRENT_TIMESTAMP
                        )
                        """,
                Boolean.class,
                remoteAddressHash
        ));
    }

    public void recordAttempt(String remoteAddressHash) {
        jdbcTemplate.update(
                """
                        INSERT INTO registration_throttles (
                            remote_address_hash, attempt_count, window_started_at, blocked_until, updated_at
                        ) VALUES (?, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP)
                        ON CONFLICT (remote_address_hash) DO UPDATE SET
                            attempt_count = CASE
                                WHEN registration_throttles.window_started_at
                                    <= CURRENT_TIMESTAMP - (? * INTERVAL '1 minute') THEN 1
                                ELSE registration_throttles.attempt_count + 1
                            END,
                            window_started_at = CASE
                                WHEN registration_throttles.window_started_at
                                    <= CURRENT_TIMESTAMP - (? * INTERVAL '1 minute')
                                    THEN CURRENT_TIMESTAMP
                                ELSE registration_throttles.window_started_at
                            END,
                            blocked_until = CASE
                                WHEN CASE
                                    WHEN registration_throttles.window_started_at
                                        <= CURRENT_TIMESTAMP - (? * INTERVAL '1 minute') THEN 1
                                    ELSE registration_throttles.attempt_count + 1
                                END >= ? THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 minute')
                                ELSE NULL
                            END,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                remoteAddressHash,
                WINDOW_MINUTES,
                WINDOW_MINUTES,
                WINDOW_MINUTES,
                MAX_ATTEMPTS,
                WINDOW_MINUTES
        );
    }
}
