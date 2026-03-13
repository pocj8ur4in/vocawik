package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.Playlist;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Playlist} persistence access. */
public interface PlaylistRepository
        extends JpaRepository<Playlist, Long>, PlaylistCriteriaRepository {

    /** Finds a playlist by resource UUID, including deleted resources. */
    Optional<Playlist> findByResourceUuid(UUID resourceUuid);

    /** Finds an active playlist by resource UUID. */
    Optional<Playlist> findByResourceUuidAndResourceIsDeletedFalse(UUID resourceUuid);

    /** Finds public playlists by shared resource IDs. */
    List<Playlist> findAllByIdInAndIsPublicTrue(Collection<Long> ids);
}
