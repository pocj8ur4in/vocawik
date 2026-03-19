package com.vocawik.service.stats;

import com.vocawik.domain.stats.DailyStat;
import com.vocawik.dto.stats.StatsResponse;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.history.HistoryRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.stats.DailyStatRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for daily aggregate stats snapshots. */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final DailyStatRepository dailyStatRepository;
    private final SongRepository songRepository;
    private final VocalRepository vocalRepository;
    private final ArtistRepository artistRepository;
    private final HistoryRepository historyRepository;

    /** Returns the latest recorded stats snapshot. */
    @Transactional(readOnly = true)
    public StatsResponse getLatest() {
        DailyStat stat =
                dailyStatRepository
                        .findTopByOrderByStatsDateDesc()
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(stat);
    }

    /** Records or updates the stats snapshot for the given UTC date. */
    @Transactional
    public StatsResponse recordDailyStats(LocalDate statsDate) {
        if (statsDate == null) {
            throw new IllegalArgumentException("statsDate is required");
        }

        long songCount = songRepository.countByResourceIsDeletedFalse();
        long vocalCount = vocalRepository.countByResourceIsDeletedFalse();
        long artistCount = artistRepository.countByResourceIsDeletedFalse();
        long documentContributorCount =
                historyRepository.countDistinctActorUsers()
                        + historyRepository.countDistinctActorGuests();
        long historyCount = historyRepository.count();

        DailyStat stat =
                dailyStatRepository
                        .findById(statsDate)
                        .map(
                                existing -> {
                                    existing.updateCounts(
                                            songCount,
                                            vocalCount,
                                            artistCount,
                                            documentContributorCount,
                                            historyCount);
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        dailyStatRepository.save(
                                                DailyStat.create(
                                                        statsDate,
                                                        songCount,
                                                        vocalCount,
                                                        artistCount,
                                                        documentContributorCount,
                                                        historyCount)));

        return toResponse(stat);
    }

    private StatsResponse toResponse(DailyStat stat) {
        return new StatsResponse(
                stat.getStatsDate(),
                stat.getSongCount(),
                stat.getVocalCount(),
                stat.getArtistCount(),
                stat.getDocumentContributorCount(),
                stat.getHistoryCount());
    }
}
