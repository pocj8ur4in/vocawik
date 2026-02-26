package com.vocawik.repository.artist;

import com.vocawik.domain.artist.ArtistLink;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link ArtistLink} persistence access. */
public interface ArtistLinkRepository extends JpaRepository<ArtistLink, Long> {}
