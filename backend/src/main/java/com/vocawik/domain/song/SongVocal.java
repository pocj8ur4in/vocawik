package com.vocawik.domain.song;

import com.vocawik.domain.BaseEntity;
import com.vocawik.domain.vocal.Vocal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Mapping entity between songs and vocals. */
@Getter
@Entity
@Table(
        name = "song_vocals",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_song_vocals_song_vocal",
                    columnNames = {"song_id", "vocal_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongVocal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vocal_id", nullable = false)
    private Vocal vocal;

    @Column(name = "is_main", nullable = false)
    private boolean isMain;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a song-vocal mapping row.
     *
     * @param song target song
     * @param vocal target vocal
     * @param isMain whether vocal is main participant
     * @param sortOrder display order
     * @return created mapping row
     */
    public static SongVocal create(Song song, Vocal vocal, boolean isMain, int sortOrder) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (vocal == null) {
            throw new IllegalArgumentException("vocal is required");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        SongVocal songVocal = new SongVocal();
        songVocal.song = song;
        songVocal.vocal = vocal;
        songVocal.isMain = isMain;
        songVocal.sortOrder = sortOrder;
        return songVocal;
    }

    /**
     * Updates participant flags and ordering.
     *
     * @param isMain updated main flag
     * @param sortOrder updated sort order
     */
    public void updateParticipation(boolean isMain, int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }
        this.isMain = isMain;
        this.sortOrder = sortOrder;
    }
}
