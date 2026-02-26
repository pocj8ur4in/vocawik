package com.vocawik.repository.song;

import com.vocawik.domain.song.SongPv;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link SongPv} persistence access. */
public interface SongPvRepository extends JpaRepository<SongPv, Long> {}
