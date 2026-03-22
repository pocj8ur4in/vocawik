package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.PlaylistSong;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link PlaylistSong} persistence access. */
public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Long> {

    /** Finds all playlist-song rows by song id in display order. */
    java.util.List<PlaylistSong> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Finds all playlist-song rows by playlist id in display order. */
    java.util.List<PlaylistSong> findAllByPlaylistIdOrderBySortOrderAscIdAsc(Long playlistId);

    /** Finds all playlist-song rows with song resources preloaded in display order. */
    @Query(
            """
            select ps
            from PlaylistSong ps
                join fetch ps.song s
                join fetch s.resource
            where ps.playlist.id = :playlistId
            order by ps.sortOrder asc, ps.id asc
            """)
    List<PlaylistSong> findAllWithSongResourceByPlaylistIdOrderBySortOrderAscIdAsc(
            @Param("playlistId") Long playlistId);

    /** Deletes all playlist-song rows for a playlist. */
    @Modifying
    @Transactional
    @Query("delete from PlaylistSong ps where ps.playlist.id = :playlistId")
    void deleteByPlaylistId(Long playlistId);
}
