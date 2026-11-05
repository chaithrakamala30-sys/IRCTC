package in.railconnect.core.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SuperAdminSafetyLock {
    private static final long LOCK_KEY = 731846205193L;
    private final JdbcTemplate jdbcTemplate;

    public SuperAdminSafetyLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquire() {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?)",
                result -> {
                    if (result.next()) {
                        result.getObject(1);
                    }
                    return null;
                },
                LOCK_KEY
        );
    }
}
