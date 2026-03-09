package com.vocawik.repository.song;

import com.vocawik.domain.song.SongArtist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongArtist} persistence access. */
public interface SongArtistRepository extends JpaRepository<SongArtist, Long> {

    /** Finds all song-artist rows by song id in display order. */
    java.util.List<SongArtist> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Deletes all song-artist rows by song id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongArtist sa where sa.song.id = :songId")
    void deleteBySongId(@Param("songId") Long songId);
}
