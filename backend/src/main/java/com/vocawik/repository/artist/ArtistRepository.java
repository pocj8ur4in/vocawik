package com.vocawik.repository.artist;

import com.vocawik.domain.artist.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Artist} persistence access. */
public interface ArtistRepository extends JpaRepository<Artist, Long>, ArtistCriteriaRepository {}
