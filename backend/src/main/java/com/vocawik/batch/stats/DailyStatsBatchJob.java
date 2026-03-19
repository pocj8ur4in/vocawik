package com.vocawik.batch.stats;

import com.vocawik.service.stats.StatsService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily UTC batch job that records aggregate site stats. */
@Component
@RequiredArgsConstructor
public class DailyStatsBatchJob {

    private final StatsService statsService;
    private final Clock batchClock;

    /** Records the daily stats snapshot every day at 00:00 UTC. */
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void recordDailyStats() {
        statsService.recordDailyStats(LocalDate.now(batchClock));
    }
}
