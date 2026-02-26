package com.vocawik.domain.song;

import com.fasterxml.jackson.databind.JsonNode;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
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

    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "lang_codes", nullable = false, columnDefinition = "text[]")
    private String[] langCodeValues;

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
     * @param langCodes lyric languages
     * @param lyrics lyric body payload
     * @param isPrimary whether this lyric is primary for the language
     * @param sortOrder ordering in same song/language
     * @return created lyric row
     */
    public static SongLyric create(
            Song song, Set<Language> langCodes, JsonNode lyrics, boolean isPrimary, int sortOrder) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (lyrics == null || lyrics.isNull()) {
            throw new IllegalArgumentException("lyrics is required");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        SongLyric songLyric = new SongLyric();
        songLyric.song = song;
        songLyric.langCodeValues = normalizeLangCodes(langCodes);
        songLyric.lyrics = lyrics;
        songLyric.isPrimary = isPrimary;
        songLyric.sortOrder = sortOrder;
        return songLyric;
    }

    /**
     * Returns lyric languages as set.
     *
     * @return normalized language set
     */
    public Set<Language> getLangCodes() {
        if (langCodeValues == null || langCodeValues.length == 0) {
            return Set.of();
        }
        Set<Language> normalized =
                Arrays.stream(langCodeValues)
                        .map(Language::valueOf)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(normalized);
    }

    /**
     * Updates lyric languages.
     *
     * @param langCodes updated lyric languages
     */
    public void updateLangCodes(Set<Language> langCodes) {
        this.langCodeValues = normalizeLangCodes(langCodes);
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

    private static String[] normalizeLangCodes(Set<Language> langCodes) {
        if (langCodes == null || langCodes.isEmpty()) {
            throw new IllegalArgumentException("langCodes is required");
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (Language language : langCodes) {
            if (language == null) {
                throw new IllegalArgumentException("langCodes contains null");
            }
            normalized.add(language.name());
        }
        return normalized.toArray(new String[0]);
    }
}
