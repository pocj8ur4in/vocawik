package com.vocawik.repository.artist;

import com.vocawik.domain.artist.Artist;
import com.vocawik.repository.common.ResourceRefProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Artist} persistence access. */
public interface ArtistRepository extends JpaRepository<Artist, Long>, ArtistCriteriaRepository {

    /** Counts active artists. */
    long countByResourceIsDeletedFalse();

    /**
     * Finds an artist by resource UUID, including deleted resources.
     *
     * @param resourceUuid artist resource UUID
     * @return matching artist when found
     */
    Optional<Artist> findByResourceUuid(UUID resourceUuid);

    /**
     * Finds an active artist by resource UUID.
     *
     * @param resourceUuid artist resource UUID
     * @return matching artist when found
     */
    Optional<Artist> findByResourceUuidAndResourceIsDeletedFalse(UUID resourceUuid);

    /**
     * Finds active artists by resource UUIDs.
     *
     * @param resourceUuids artist resource UUIDs
     * @return matching active artists
     */
    @Query(
            """
            select a.id as id, a.resource.uuid as resourceUuid
            from Artist a
            where a.resource.isDeleted = false
              and a.resource.uuid in :resourceUuids
            """)
    List<ResourceRefProjection> findResourceRefsByResourceUuids(
            @Param("resourceUuids") Collection<java.util.UUID> resourceUuids);
}
