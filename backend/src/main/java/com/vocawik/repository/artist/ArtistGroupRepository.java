package com.vocawik.repository.artist;

import com.vocawik.domain.artist.ArtistGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link ArtistGroup} persistence access. */
public interface ArtistGroupRepository extends JpaRepository<ArtistGroup, Long> {

    /** Finds all group rows by group artist id in display order. */
    List<ArtistGroup> findAllByGroupArtistIdOrderBySortOrderAscIdAsc(Long groupArtistId);

    /** Finds all membership rows by member artist id in display order. */
    List<ArtistGroup> findAllByMemberArtistIdOrderBySortOrderAscIdAsc(Long memberArtistId);

    /** Deletes all group rows by group artist id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ArtistGroup ag where ag.groupArtist.id = :groupArtistId")
    void deleteByGroupArtistId(@Param("groupArtistId") Long groupArtistId);
}
