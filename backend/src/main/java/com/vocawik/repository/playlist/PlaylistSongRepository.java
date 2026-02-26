package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.PlaylistSong;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link PlaylistSong} persistence access. */
public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Long> {}
