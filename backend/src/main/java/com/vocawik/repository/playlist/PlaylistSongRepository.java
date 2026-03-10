package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.PlaylistSong;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Repository for {@link PlaylistSong} persistence access. */
public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Long> {

    /** Finds all playlist-song rows by song id in display order. */
    java.util.List<PlaylistSong> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Finds all playlist-song rows by playlist id in display order. */
    java.util.List<PlaylistSong> findAllByPlaylistIdOrderBySortOrderAscIdAsc(Long playlistId);

    /** Deletes all playlist-song rows for a playlist. */
    @Modifying
    @Transactional
    @Query("delete from PlaylistSong ps where ps.playlist.id = :playlistId")
    void deleteByPlaylistId(Long playlistId);
}
