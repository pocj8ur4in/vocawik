package com.vocawik.repository.song;

import com.vocawik.domain.song.SongLink;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link SongLink} persistence access. */
public interface SongLinkRepository extends JpaRepository<SongLink, Long> {}
