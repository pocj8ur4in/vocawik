package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.Playlist;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Playlist} persistence access. */
public interface PlaylistRepository
        extends JpaRepository<Playlist, Long>, PlaylistCriteriaRepository {

    /** Finds an active playlist by resource UUID. */
    Optional<Playlist> findByResourceUuidAndResourceIsDeletedFalse(UUID resourceUuid);
}
