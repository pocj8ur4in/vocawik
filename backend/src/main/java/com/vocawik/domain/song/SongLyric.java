package com.vocawik.domain.song;

import com.fasterxml.jackson.databind.JsonNode;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Localized lyric entry for a song. */
@Getter
@Entity
@Table(name = "song_lyrics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongLyric extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Enumerated(EnumType.STRING)
    @Column(name = "lang_code", nullable = false, length = 10)
    private Language langCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lyrics", nullable = false, columnDefinition = "jsonb")
    private JsonNode lyrics;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a localized lyric row.
     *
     * @param song target song
     * @param langCode lyric language
     * @param lyrics lyric body payload
     * @param isPrimary whether this lyric is primary for the language
     * @param sortOrder ordering in same song/language
     * @return created lyric row
     */
    public static SongLyric create(
            Song song, Language langCode, JsonNode lyrics, boolean isPrimary, int sortOrder) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (langCode == null) {
            throw new IllegalArgumentException("langCode is required");
        }
        if (lyrics == null || lyrics.isNull()) {
            throw new IllegalArgumentException("lyrics is required");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        SongLyric songLyric = new SongLyric();
        songLyric.song = song;
        songLyric.langCode = langCode;
        songLyric.lyrics = lyrics;
        songLyric.isPrimary = isPrimary;
        songLyric.sortOrder = sortOrder;
        return songLyric;
    }

    /**
     * Updates lyric text.
     *
     * @param lyrics updated lyric payload
     */
    public void updateLyrics(JsonNode lyrics) {
        if (lyrics == null || lyrics.isNull()) {
            throw new IllegalArgumentException("lyrics is required");
        }
        this.lyrics = lyrics;
    }
}
