package com.vocawik.repository.song;

import com.vocawik.domain.song.SongPvView;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link SongPvView} persistence access. */
public interface SongPvViewRepository extends JpaRepository<SongPvView, Long> {}
