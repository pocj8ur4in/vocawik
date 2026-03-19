package com.vocawik.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Batch module configuration. */
@Configuration
@EnableScheduling
public class BatchConfig {

    @Bean
    public Clock batchClock() {
        return Clock.systemUTC();
    }
}
