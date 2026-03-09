package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.VocalCharacter;
import com.vocawik.repository.common.ResourceRefProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link VocalCharacter} persistence access. */
public interface VocalCharacterRepository
        extends JpaRepository<VocalCharacter, Long>, VocalCriteriaRepository {

    /** Finds an active vocal character by resource UUID. */
    java.util.Optional<VocalCharacter> findByResourceUuidAndResourceIsDeletedFalse(
            java.util.UUID resourceUuid);

    /**
     * Finds active vocal characters by resource UUIDs.
     *
     * @param resourceUuids vocal resource UUIDs
     * @return matching active vocal characters
     */
    @Query(
            """
            select v.id as id, v.resource.uuid as resourceUuid
            from VocalCharacter v
            where v.resource.isDeleted = false
              and v.resource.uuid in :resourceUuids
            """)
    List<ResourceRefProjection> findResourceRefsByResourceUuids(
            @Param("resourceUuids") Collection<java.util.UUID> resourceUuids);
}
