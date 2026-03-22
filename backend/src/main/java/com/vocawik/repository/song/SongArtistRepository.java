package com.vocawik.repository.song;

import com.vocawik.domain.song.SongArtist;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongArtist} persistence access. */
public interface SongArtistRepository extends JpaRepository<SongArtist, Long> {

    /** Finds all song-artist rows by song id in display order. */
    java.util.List<SongArtist> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Finds all song-artist rows by song ids with artist resources in display order. */
    @Query(
            """
            select sa
            from SongArtist sa
                join fetch sa.artist a
                join fetch a.resource
            where sa.song.id in :songIds
            order by sa.song.id asc, sa.sortOrder asc, sa.id asc
            """)
    java.util.List<SongArtist> findAllWithArtistResourceBySongIdInOrderBySongIdAscSortOrderAscIdAsc(
            @Param("songIds") Collection<Long> songIds);

    /** Finds all song-artist rows by artist id in display order. */
    java.util.List<SongArtist> findAllByArtistIdOrderBySortOrderAscIdAsc(Long artistId);

    /** Counts all song-artist rows by artist id. */
    long countByArtistId(Long artistId);

    /** Finds recent song-artist rows ordered by song published time. */
    @Query(
            """
            select sa
            from SongArtist sa
                join sa.song s
            where sa.artist.id = :artistId
            order by
                case when s.publishedAt is null then 1 else 0 end asc,
                s.publishedAt desc,
                sa.id desc
            """)
    List<SongArtist> findRecentByArtistId(@Param("artistId") Long artistId, Pageable pageable);

    /** Finds popular song-artist rows ordered by song resource view count. */
    @Query(
            """
            select sa
            from SongArtist sa
                join sa.song s
                join s.resource r
            where sa.artist.id = :artistId
            order by r.viewCount desc, r.createdAt desc, sa.id desc
            """)
    List<SongArtist> findPopularByArtistId(@Param("artistId") Long artistId, Pageable pageable);

    /** Deletes all song-artist rows by song id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongArtist sa where sa.song.id = :songId")
    void deleteBySongId(@Param("songId") Long songId);
}
