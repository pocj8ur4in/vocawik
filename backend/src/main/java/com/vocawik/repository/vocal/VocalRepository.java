package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.Vocal;
import com.vocawik.repository.common.ResourceRefProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Vocal} persistence access. */
public interface VocalRepository extends JpaRepository<Vocal, Long>, VocalCriteriaRepository {

    /** Counts active vocals. */
    long countByResourceIsDeletedFalse();

    /** Finds a vocal by resource UUID, including deleted resources. */
    java.util.Optional<Vocal> findByResourceUuid(java.util.UUID resourceUuid);

    /** Finds an active vocal by resource UUID. */
    java.util.Optional<Vocal> findByResourceUuidAndResourceIsDeletedFalse(
            java.util.UUID resourceUuid);

    /**
     * Finds active vocals by resource UUIDs.
     *
     * @param resourceUuids vocal resource UUIDs
     * @return matching active vocals
     */
    @Query(
            """
            select v.id as id, v.resource.uuid as resourceUuid
            from Vocal v
            where v.resource.isDeleted = false
              and v.resource.uuid in :resourceUuids
            """)
    List<ResourceRefProjection> findResourceRefsByResourceUuids(
            @Param("resourceUuids") Collection<java.util.UUID> resourceUuids);
}
