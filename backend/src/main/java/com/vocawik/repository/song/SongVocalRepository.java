package com.vocawik.repository.song;

import com.vocawik.domain.song.SongVocal;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link SongVocal} persistence access. */
public interface SongVocalRepository extends JpaRepository<SongVocal, Long> {}
