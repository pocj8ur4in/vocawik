package com.vocawik.dto.stats;

import java.time.LocalDate;

/** Latest daily aggregate stats payload. */
public record StatsResponse(
        LocalDate statsDate,
        long songCount,
        long vocalCount,
        long artistCount,
        long documentContributorCount,
        long historyCount) {}
