package com.vocawik.repository.song;

import com.vocawik.domain.song.Song;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Song} persistence access. */
public interface SongRepository extends JpaRepository<Song, Long>, SongSearchRepository {}
