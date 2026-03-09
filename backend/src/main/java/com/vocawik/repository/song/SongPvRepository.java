package com.vocawik.repository.song;

import com.vocawik.domain.song.SongPv;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongPv} persistence access. */
public interface SongPvRepository extends JpaRepository<SongPv, Long> {

    /** Finds all PV rows by song id in display order. */
    List<SongPv> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Finds PV ids by song id. */
    @Query("select sp.id from SongPv sp where sp.song.id = :songId")
    List<Long> findIdsBySongId(@Param("songId") Long songId);

    /** Deletes all PV rows by song id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongPv sp where sp.song.id = :songId")
    void deleteBySongId(@Param("songId") Long songId);
}
