package com.vocawik.repository.song;

import com.vocawik.domain.song.SongArtist;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link SongArtist} persistence access. */
public interface SongArtistRepository extends JpaRepository<SongArtist, Long> {}
