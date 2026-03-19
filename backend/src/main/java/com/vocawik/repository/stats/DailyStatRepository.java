package com.vocawik.repository.stats;

import com.vocawik.domain.stats.DailyStat;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link DailyStat} persistence access. */
public interface DailyStatRepository extends JpaRepository<DailyStat, java.time.LocalDate> {

    /** Finds the latest recorded daily stats row. */
    Optional<DailyStat> findTopByOrderByStatsDateDesc();
}
