package com.vocawik.repository.song;

import com.vocawik.domain.song.SongPvView;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongPvView} persistence access. */
public interface SongPvViewRepository extends JpaRepository<SongPvView, Long> {

    /** Finds all PV view rows by PV ids. */
    java.util.List<SongPvView> findAllBySongPvIdIn(Collection<Long> songPvIds);

    /** Deletes all PV view rows by PV ids. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongPvView spv where spv.songPv.id in :songPvIds")
    void deleteBySongPvIds(@Param("songPvIds") Collection<Long> songPvIds);
}
