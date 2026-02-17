package com.vocawik.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cache configuration.
 *
 * <p>Cache entries are stored in Redis with JSON serialization.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private final String cacheKeyPrefix;

    /**
     * Creates cache configuration with the given Redis key prefix.
     *
     * @param cacheKeyPrefix key prefix applied to all cache entries
     */
    public CacheConfig(@Value("${cache.key-prefix:vocawik}") String cacheKeyPrefix) {
        this.cacheKeyPrefix = cacheKeyPrefix;
    }

    /**
     * Creates a {@link RedisCacheManager} with default and per-cache Redis policies.
     *
     * @param connectionFactory the Redis connection factory
     * @return configured RedisCacheManager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfiguration())
                .withInitialCacheConfigurations(initialCacheConfigurations())
                .build();
    }

    private RedisCacheConfiguration defaultCacheConfiguration() {
        return baseConfiguration()
                .entryTtl(Duration.ofHours(1))
                .computePrefixWith(cacheName -> buildPrefix(cacheName));
    }

    private Map<String, RedisCacheConfiguration> initialCacheConfigurations() {
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();

        return configs;
    }

    private RedisCacheConfiguration baseConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                RedisSerializer.json()));
    }

    private String buildPrefix(String cacheName) {
        return cacheKeyPrefix + ":" + cacheName + "::";
    }
}
