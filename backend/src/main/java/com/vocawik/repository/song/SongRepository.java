package com.vocawik.repository.song;

import com.vocawik.domain.song.Song;
import com.vocawik.repository.common.ResourceRefProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Song} persistence access. */
public interface SongRepository extends JpaRepository<Song, Long>, SongCriteriaRepository {

    /** Counts active songs. */
    long countByResourceIsDeletedFalse();

    /**
     * Finds a song by resource UUID, including deleted resources.
     *
     * @param resourceUuid song resource UUID
     * @return matching song when found
     */
    Optional<Song> findByResourceUuid(UUID resourceUuid);

    /**
     * Finds an active song by resource UUID.
     *
     * @param resourceUuid song resource UUID
     * @return matching song when found
     */
    Optional<Song> findByResourceUuidAndResourceIsDeletedFalse(UUID resourceUuid);

    /**
     * Finds active songs by resource UUIDs.
     *
     * @param resourceUuids song resource UUIDs
     * @return matching active songs
     */
    @Query(
            """
            select s.id as id, s.resource.uuid as resourceUuid
            from Song s
            where s.resource.isDeleted = false
              and s.resource.uuid in :resourceUuids
            """)
    List<ResourceRefProjection> findResourceRefsByResourceUuids(
            @Param("resourceUuids") Collection<java.util.UUID> resourceUuids);
}
