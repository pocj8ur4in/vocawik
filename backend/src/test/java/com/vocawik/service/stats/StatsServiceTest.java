package com.vocawik.service.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vocawik.domain.stats.DailyStat;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.history.HistoryRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.stats.DailyStatRepository;
import com.vocawik.repository.vocal.VocalRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

    @Test
    @DisplayName("Record should create a new daily stats row when date is missing")
    void recordDailyStats_shouldCreateNewRow() {
        DailyStatRepository dailyStatRepository = mock(DailyStatRepository.class);
        SongRepository songRepository = mock(SongRepository.class);
        VocalRepository vocalRepository = mock(VocalRepository.class);
        ArtistRepository artistRepository = mock(ArtistRepository.class);
        HistoryRepository historyRepository = mock(HistoryRepository.class);
        StatsService statsService =
                new StatsService(
                        dailyStatRepository,
                        songRepository,
                        vocalRepository,
                        artistRepository,
                        historyRepository);

        LocalDate statsDate = LocalDate.of(2026, 3, 20);
        when(dailyStatRepository.findById(eq(statsDate))).thenReturn(Optional.empty());
        when(songRepository.countByResourceIsDeletedFalse()).thenReturn(12_482L);
        when(vocalRepository.countByResourceIsDeletedFalse()).thenReturn(1_245L);
        when(artistRepository.countByResourceIsDeletedFalse()).thenReturn(3_120L);
        when(historyRepository.countDistinctActorUsers()).thenReturn(8_421L);
        when(historyRepository.countDistinctActorGuests()).thenReturn(579L);
        when(historyRepository.count()).thenReturn(54_321L);
        when(dailyStatRepository.save(org.mockito.ArgumentMatchers.any(DailyStat.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = statsService.recordDailyStats(statsDate);

        assertThat(result.statsDate()).isEqualTo(statsDate);
        assertThat(result.songCount()).isEqualTo(12_482L);
        assertThat(result.vocalCount()).isEqualTo(1_245L);
        assertThat(result.artistCount()).isEqualTo(3_120L);
        assertThat(result.documentContributorCount()).isEqualTo(9_000L);
        assertThat(result.historyCount()).isEqualTo(54_321L);
    }

    @Test
    @DisplayName("Record-if-empty should create today's row when stats table is empty")
    void recordDailyStatsIfEmpty_shouldCreateNewRowWhenTableIsEmpty() {
        DailyStatRepository dailyStatRepository = mock(DailyStatRepository.class);
        SongRepository songRepository = mock(SongRepository.class);
        VocalRepository vocalRepository = mock(VocalRepository.class);
        ArtistRepository artistRepository = mock(ArtistRepository.class);
        HistoryRepository historyRepository = mock(HistoryRepository.class);
        StatsService statsService =
                new StatsService(
                        dailyStatRepository,
                        songRepository,
                        vocalRepository,
                        artistRepository,
                        historyRepository);

        LocalDate statsDate = LocalDate.of(2026, 3, 20);
        when(dailyStatRepository.findTopByOrderByStatsDateDesc()).thenReturn(Optional.empty());
        when(dailyStatRepository.findById(eq(statsDate))).thenReturn(Optional.empty());
        when(songRepository.countByResourceIsDeletedFalse()).thenReturn(12_482L);
        when(vocalRepository.countByResourceIsDeletedFalse()).thenReturn(1_245L);
        when(artistRepository.countByResourceIsDeletedFalse()).thenReturn(3_120L);
        when(historyRepository.countDistinctActorUsers()).thenReturn(8_421L);
        when(historyRepository.countDistinctActorGuests()).thenReturn(579L);
        when(historyRepository.count()).thenReturn(54_321L);
        when(dailyStatRepository.save(org.mockito.ArgumentMatchers.any(DailyStat.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = statsService.recordDailyStatsIfEmpty(statsDate);

        assertThat(result.statsDate()).isEqualTo(statsDate);
        assertThat(result.songCount()).isEqualTo(12_482L);
    }

    @Test
    @DisplayName("Record-if-empty should return latest row when stats already exist")
    void recordDailyStatsIfEmpty_shouldSkipRecountWhenTableHasRows() {
        DailyStatRepository dailyStatRepository = mock(DailyStatRepository.class);
        SongRepository songRepository = mock(SongRepository.class);
        VocalRepository vocalRepository = mock(VocalRepository.class);
        ArtistRepository artistRepository = mock(ArtistRepository.class);
        HistoryRepository historyRepository = mock(HistoryRepository.class);
        StatsService statsService =
                new StatsService(
                        dailyStatRepository,
                        songRepository,
                        vocalRepository,
                        artistRepository,
                        historyRepository);

        DailyStat latest = DailyStat.create(LocalDate.of(2026, 3, 19), 1L, 2L, 3L, 4L, 5L);
        when(dailyStatRepository.findTopByOrderByStatsDateDesc()).thenReturn(Optional.of(latest));

        var result = statsService.recordDailyStatsIfEmpty(LocalDate.of(2026, 3, 20));

        assertThat(result.statsDate()).isEqualTo(LocalDate.of(2026, 3, 19));
        assertThat(result.songCount()).isEqualTo(1L);
        verifyNoInteractions(songRepository, vocalRepository, artistRepository, historyRepository);
    }

    @Test
    @DisplayName("Get latest should return the most recent stats row")
    void getLatest_shouldReturnLatestRow() {
        DailyStatRepository dailyStatRepository = mock(DailyStatRepository.class);
        StatsService statsService =
                new StatsService(
                        dailyStatRepository,
                        mock(SongRepository.class),
                        mock(VocalRepository.class),
                        mock(ArtistRepository.class),
                        mock(HistoryRepository.class));
        DailyStat dailyStat = DailyStat.create(LocalDate.of(2026, 3, 20), 1L, 2L, 3L, 4L, 5L);
        when(dailyStatRepository.findTopByOrderByStatsDateDesc())
                .thenReturn(Optional.of(dailyStat));

        var result = statsService.getLatest();

        assertThat(result.statsDate()).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(result.songCount()).isEqualTo(1L);
        assertThat(result.vocalCount()).isEqualTo(2L);
        assertThat(result.artistCount()).isEqualTo(3L);
        assertThat(result.documentContributorCount()).isEqualTo(4L);
        assertThat(result.historyCount()).isEqualTo(5L);
    }
}
