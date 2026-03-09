package com.vocawik.repository.song;

import com.vocawik.domain.song.SongRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongRelation} persistence access. */
public interface SongRelationRepository extends JpaRepository<SongRelation, Long> {

    /** Finds all outgoing relations by source song id. */
    java.util.List<SongRelation> findAllBySourceSongIdOrderByIdAsc(Long songId);

    /** Finds all incoming relations by target song id. */
    java.util.List<SongRelation> findAllByTargetSongIdOrderByIdAsc(Long songId);

    /** Deletes all outgoing relations by source song id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongRelation sr where sr.sourceSong.id = :songId")
    void deleteBySourceSongId(@Param("songId") Long songId);
}
