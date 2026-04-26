package com.vocawik.module.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Provides shared JPA auditing infrastructure. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "persistenceDateTimeProvider")
public class JpaConfiguration {

    /**
     * Creates the default UTC clock used by persistence auditing.
     *
     * @return UTC system clock
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock persistenceClock() {
        return Clock.systemUTC();
    }

    /**
     * Creates the auditing time provider backed by the application clock.
     *
     * @param clock clock used to obtain the current instant
     * @return auditing time provider
     */
    @Bean
    public DateTimeProvider persistenceDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
