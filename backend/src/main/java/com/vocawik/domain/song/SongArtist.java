package com.vocawik.domain.song;

import com.vocawik.domain.BaseEntity;
import com.vocawik.domain.artist.Artist;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Mapping entity between songs and artists. */
@Getter
@Entity
@Table(
        name = "song_artists",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_song_artists_song_artist_role",
                    columnNames = {"song_id", "artist_id", "role"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongArtist extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private SongArtistRole role;

    @Column(name = "is_main", nullable = false)
    private boolean isMain;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a song-artist mapping row.
     *
     * @param song target song
     * @param artist target artist
     * @param role participation role
     * @param isMain whether artist is main participant
     * @param sortOrder display order
     * @return created mapping row
     */
    public static SongArtist create(
            Song song, Artist artist, SongArtistRole role, boolean isMain, int sortOrder) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (artist == null) {
            throw new IllegalArgumentException("artist is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        SongArtist songArtist = new SongArtist();
        songArtist.song = song;
        songArtist.artist = artist;
        songArtist.role = role;
        songArtist.isMain = isMain;
        songArtist.sortOrder = sortOrder;
        return songArtist;
    }
}
