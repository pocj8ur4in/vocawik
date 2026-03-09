package com.vocawik.repository.song;

import com.vocawik.domain.song.SongLyric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongLyric} persistence access. */
public interface SongLyricRepository extends JpaRepository<SongLyric, Long> {

    /** Finds all lyrics by song id in display order. */
    java.util.List<SongLyric> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Deletes all lyrics by song id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongLyric sl where sl.song.id = :songId")
    void deleteBySongId(@Param("songId") Long songId);
}
