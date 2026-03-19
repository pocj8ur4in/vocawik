package com.vocawik.domain.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One daily snapshot row for site-wide aggregate stats. */
@Getter
@Entity
@Table(name = "stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyStat {

    @Id
    @Column(name = "stats_date", nullable = false)
    private LocalDate statsDate;

    @Column(name = "song_count", nullable = false)
    private long songCount;

    @Column(name = "vocal_count", nullable = false)
    private long vocalCount;

    @Column(name = "artist_count", nullable = false)
    private long artistCount;

    @Column(name = "document_contributor_count", nullable = false)
    private long documentContributorCount;

    @Column(name = "history_count", nullable = false)
    private long historyCount;

    /** Creates a new daily stats snapshot. */
    public static DailyStat create(
            LocalDate statsDate,
            long songCount,
            long vocalCount,
            long artistCount,
            long documentContributorCount,
            long historyCount) {
        if (statsDate == null) {
            throw new IllegalArgumentException("statsDate is required");
        }
        validateNonNegative(songCount, "songCount");
        validateNonNegative(vocalCount, "vocalCount");
        validateNonNegative(artistCount, "artistCount");
        validateNonNegative(documentContributorCount, "documentContributorCount");
        validateNonNegative(historyCount, "historyCount");

        DailyStat stat = new DailyStat();
        stat.statsDate = statsDate;
        stat.songCount = songCount;
        stat.vocalCount = vocalCount;
        stat.artistCount = artistCount;
        stat.documentContributorCount = documentContributorCount;
        stat.historyCount = historyCount;
        return stat;
    }

    /** Updates counts for an existing daily snapshot. */
    public void updateCounts(
            long songCount,
            long vocalCount,
            long artistCount,
            long documentContributorCount,
            long historyCount) {
        validateNonNegative(songCount, "songCount");
        validateNonNegative(vocalCount, "vocalCount");
        validateNonNegative(artistCount, "artistCount");
        validateNonNegative(documentContributorCount, "documentContributorCount");
        validateNonNegative(historyCount, "historyCount");

        this.songCount = songCount;
        this.vocalCount = vocalCount;
        this.artistCount = artistCount;
        this.documentContributorCount = documentContributorCount;
        this.historyCount = historyCount;
    }

    private static void validateNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
    }
}
