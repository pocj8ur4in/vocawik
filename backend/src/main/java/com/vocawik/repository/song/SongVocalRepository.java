package com.vocawik.repository.song;

import com.vocawik.domain.song.SongVocal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongVocal} persistence access. */
public interface SongVocalRepository extends JpaRepository<SongVocal, Long> {

    /** Finds all song-vocal rows by song id in display order. */
    java.util.List<SongVocal> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Deletes all song-vocal rows by song id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongVocal sv where sv.song.id = :songId")
    void deleteBySongId(@Param("songId") Long songId);
}
