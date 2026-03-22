package com.vocawik.repository.song;

import com.vocawik.domain.song.SongVocal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongVocal} persistence access. */
public interface SongVocalRepository extends JpaRepository<SongVocal, Long> {

    /** Finds all song-vocal rows by song id in display order. */
    List<SongVocal> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Finds all song-vocal rows by song ids with vocal resources in display order. */
    @Query(
            """
            select sv
            from SongVocal sv
                join fetch sv.vocal v
                join fetch v.resource
            where sv.song.id in :songIds
            order by sv.song.id asc, sv.sortOrder asc, sv.id asc
            """)
    List<SongVocal> findAllWithVocalResourceBySongIdInOrderBySongIdAscSortOrderAscIdAsc(
            @Param("songIds") Collection<Long> songIds);

    /** Finds all song-vocal rows by vocal id in display order. */
    List<SongVocal> findAllByVocalIdOrderBySortOrderAscIdAsc(Long vocalId);

    /** Counts all song-vocal rows by vocal id. */
    long countByVocalId(Long vocalId);

    /** Finds recent song-vocal rows ordered by song published time. */
    @Query(
            """
            select sv
            from SongVocal sv
                join sv.song s
            where sv.vocal.id = :vocalId
            order by
                case when s.publishedAt is null then 1 else 0 end asc,
                s.publishedAt desc,
                sv.id desc
            """)
    List<SongVocal> findRecentByVocalId(@Param("vocalId") Long vocalId, Pageable pageable);

    /** Finds popular song-vocal rows ordered by song resource view count. */
    @Query(
            """
            select sv
            from SongVocal sv
                join sv.song s
                join s.resource r
            where sv.vocal.id = :vocalId
            order by r.viewCount desc, r.createdAt desc, sv.id desc
            """)
    List<SongVocal> findPopularByVocalId(@Param("vocalId") Long vocalId, Pageable pageable);

    /** Deletes all song-vocal rows by song id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongVocal sv where sv.song.id = :songId")
    void deleteBySongId(@Param("songId") Long songId);
}
