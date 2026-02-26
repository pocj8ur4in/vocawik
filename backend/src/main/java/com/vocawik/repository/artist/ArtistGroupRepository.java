package com.vocawik.repository.artist;

import com.vocawik.domain.artist.ArtistGroup;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link ArtistGroup} persistence access. */
public interface ArtistGroupRepository extends JpaRepository<ArtistGroup, Long> {}
