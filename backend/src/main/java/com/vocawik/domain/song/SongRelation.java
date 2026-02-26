package com.vocawik.domain.song;

import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Relation mapping between source and target songs. */
@Getter
@Entity
@Table(
        name = "song_relations",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_song_relations_source_target",
                    columnNames = {"source_song_id", "target_song_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongRelation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_song_id", nullable = false)
    private Song sourceSong;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_song_id", nullable = false)
    private Song targetSong;

    /**
     * Creates a song relation row.
     *
     * @param sourceSong source song
     * @param targetSong target song
     * @return created relation
     */
    public static SongRelation create(Song sourceSong, Song targetSong) {
        if (sourceSong == null) {
            throw new IllegalArgumentException("sourceSong is required");
        }
        if (targetSong == null) {
            throw new IllegalArgumentException("targetSong is required");
        }
        if (sourceSong.equals(targetSong)) {
            throw new IllegalArgumentException("sourceSong and targetSong must be different");
        }

        SongRelation songRelation = new SongRelation();
        songRelation.sourceSong = sourceSong;
        songRelation.targetSong = targetSong;
        return songRelation;
    }
}
