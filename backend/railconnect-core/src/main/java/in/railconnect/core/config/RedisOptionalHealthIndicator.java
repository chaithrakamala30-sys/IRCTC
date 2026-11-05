package in.railconnect.core.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redisOptional")
@ConditionalOnProperty(name = "railconnect.redis.enabled", havingValue = "true")
public class RedisOptionalHealthIndicator implements HealthIndicator {
    private final RedisConnectionFactory connectionFactory;

    public RedisOptionalHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (var connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            if (!"PONG".equalsIgnoreCase(response)) {
                return Health.down().withDetail("reason", "Unexpected ping response").build();
            }
            return Health.up().withDetail("enabled", true).build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("enabled", true)
                    .withDetail("reason", "Optional cache connection unavailable")
                    .build();
        }
    }
}
