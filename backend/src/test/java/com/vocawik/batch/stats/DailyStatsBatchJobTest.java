package com.vocawik.batch.stats;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.vocawik.service.stats.StatsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyStatsBatchJobTest {

    @Test
    @DisplayName("Batch job should record stats for the current UTC date")
    void recordDailyStats_shouldUseCurrentUtcDate() {
        StatsService statsService = mock(StatsService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-20T12:34:56Z"), ZoneOffset.UTC);
        DailyStatsBatchJob batchJob = new DailyStatsBatchJob(statsService, fixedClock);

        batchJob.recordDailyStats();

        verify(statsService).recordDailyStats(LocalDate.of(2026, 3, 20));
    }
}
