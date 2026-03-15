package com.vocawik.domain.playlist;

import com.vocawik.domain.BaseEntity;
import com.vocawik.domain.song.Song;
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

/** Mapping entity between playlists and songs. */
@Getter
@Entity
@Table(
        name = "playlist_songs",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_playlist_songs_playlist_song",
                    columnNames = {"playlist_id", "song_id"}),
            @UniqueConstraint(
                    name = "uk_playlist_songs_playlist_sort_order",
                    columnNames = {"playlist_id", "sort_order"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistSong extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a playlist-song mapping row.
     *
     * @param playlist target playlist
     * @param song target song
     * @param sortOrder order inside playlist
     * @return created mapping
     */
    public static PlaylistSong create(Playlist playlist, Song song, int sortOrder) {
        if (playlist == null) {
            throw new IllegalArgumentException("playlist is required");
        }
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        PlaylistSong playlistSong = new PlaylistSong();
        playlistSong.playlist = playlist;
        playlistSong.song = song;
        playlistSong.sortOrder = sortOrder;
        return playlistSong;
    }

    /**
     * Updates playlist order.
     *
     * @param sortOrder updated sort order
     */
    public void updateSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }
        this.sortOrder = sortOrder;
    }
}
