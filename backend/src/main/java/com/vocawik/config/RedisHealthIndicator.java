package com.vocawik.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis.
 *
 * <p>Checks Redis by sending a PING and reports the connection status to {@code /actuator/health}.
 */
@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    /**
     * Checks Redis health by issuing a PING command.
     *
     * @return {@link Health#up()} if Redis responds with PONG, {@link Health#down()} otherwise
     */
    @Override
    public Health health() {
        try (var connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            if ("PONG".equals(pong)) {
                return Health.up().withDetail("redis", "PONG").build();
            }
            return Health.down().withDetail("redis", "unexpected response: " + pong).build();
        } catch (Exception e) {
            return Health.down(e).withDetail("redis", "connection failed").build();
        }
    }
}
