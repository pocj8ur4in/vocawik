package com.vocawik.repository.song;

import com.vocawik.domain.song.SongLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link SongLink} persistence access. */
public interface SongLinkRepository extends JpaRepository<SongLink, Long> {

    /** Finds all links by song id in insertion order. */
    List<SongLink> findAllBySongIdOrderByIdAsc(Long songId);

    /** Deletes all links by song id. */
    void deleteBySongId(Long songId);
}
