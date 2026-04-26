package com.vocawik.module.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaConfigurationTest {

    private final JpaConfiguration configuration = new JpaConfiguration();

    @Test
    @DisplayName("Should provide a UTC default clock")
    void persistenceClock_shouldUseUtc() {
        assertThat(configuration.persistenceClock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Should provide auditing time from the application clock")
    void persistenceDateTimeProvider_shouldUseProvidedClock() {
        Instant expected = Instant.parse("2026-01-02T03:04:05Z");
        Clock fixedClock = Clock.fixed(expected, ZoneOffset.UTC);

        assertThat(configuration.persistenceDateTimeProvider(fixedClock).getNow())
                .contains(expected);
    }
}
