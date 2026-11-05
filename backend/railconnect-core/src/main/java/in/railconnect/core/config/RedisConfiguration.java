package in.railconnect.core.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

@Configuration
@ConditionalOnProperty(name = "railconnect.redis.enabled", havingValue = "true")
public class RedisConfiguration {
    @Bean
    RedisConnectionFactory redisConnectionFactory(RedisProperties properties) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                properties.getHost(),
                properties.getPort());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            server.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null) {
            server.setPassword(RedisPassword.of(properties.getPassword()));
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder client =
                LettuceClientConfiguration.builder()
                        .commandTimeout(properties.getTimeout())
                        .clientOptions(ClientOptions.builder()
                                .socketOptions(SocketOptions.builder()
                                        .connectTimeout(properties.getConnectTimeout())
                                        .build())
                                .build());
        if (properties.getSsl().isEnabled()) {
            client.useSsl();
        }

        return new LettuceConnectionFactory(server, client.build());
    }
}
