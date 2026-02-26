package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Playlist} persistence access. */
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {}
