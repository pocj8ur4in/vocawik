package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.PlaylistSong;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link PlaylistSong} persistence access. */
public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Long> {

    /** Finds all playlist-song rows by song id in display order. */
    java.util.List<PlaylistSong> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Finds all playlist-song rows by playlist id in display order. */
    java.util.List<PlaylistSong> findAllByPlaylistIdOrderBySortOrderAscIdAsc(Long playlistId);
}
