package com.vocawik.repository.artist;

import com.vocawik.domain.artist.ArtistLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link ArtistLink} persistence access. */
public interface ArtistLinkRepository extends JpaRepository<ArtistLink, Long> {

    /** Finds all links by artist id in insertion order. */
    List<ArtistLink> findAllByArtistIdOrderByIdAsc(Long artistId);

    /** Deletes all links by artist id. */
    void deleteByArtistId(Long artistId);
}
