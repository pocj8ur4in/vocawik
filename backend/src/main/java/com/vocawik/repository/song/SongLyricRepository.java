package com.vocawik.repository.song;

import com.vocawik.domain.song.SongLyric;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link SongLyric} persistence access. */
public interface SongLyricRepository extends JpaRepository<SongLyric, Long> {}
