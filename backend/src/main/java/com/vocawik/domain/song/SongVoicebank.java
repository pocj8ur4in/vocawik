package com.vocawik.domain.song;

import com.vocawik.domain.BaseEntity;
import com.vocawik.domain.vocal.VocalVoicebank;
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

/** Mapping entity between songs and voicebanks. */
@Getter
@Entity
@Table(
        name = "song_voicebanks",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_song_voicebanks_song_voicebank",
                    columnNames = {"song_id", "voicebank_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongVoicebank extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voicebank_id", nullable = false)
    private VocalVoicebank voicebank;

    @Column(name = "is_main", nullable = false)
    private boolean isMain;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a song-voicebank mapping row.
     *
     * @param song target song
     * @param voicebank target voicebank
     * @param isMain whether voicebank is main participant
     * @param sortOrder display order
     * @return created mapping row
     */
    public static SongVoicebank create(
            Song song, VocalVoicebank voicebank, boolean isMain, int sortOrder) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (voicebank == null) {
            throw new IllegalArgumentException("voicebank is required");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        SongVoicebank songVoicebank = new SongVoicebank();
        songVoicebank.song = song;
        songVoicebank.voicebank = voicebank;
        songVoicebank.isMain = isMain;
        songVoicebank.sortOrder = sortOrder;
        return songVoicebank;
    }
}
