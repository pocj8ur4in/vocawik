package com.vocawik.repository.resource;

import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Resource} persistence access. */
public interface ResourceRepository
        extends JpaRepository<Resource, Long>, ResourceCriteriaRepository {

    /**
     * Finds a resource by UUID, including deleted resources.
     *
     * @param uuid resource UUID
     * @return resource when found
     */
    Optional<Resource> findByUuid(UUID uuid);

    /**
     * Finds an active resource by UUID.
     *
     * @param uuid resource UUID
     * @return active resource when found
     */
    Optional<Resource> findByUuidAndIsDeletedFalse(UUID uuid);

    /**
     * Finds active, non-deleted resources by UUIDs.
     *
     * @param uuids resource UUIDs
     * @param status resource status
     * @return matched resources
     */
    List<Resource> findAllByUuidInAndIsDeletedFalseAndStatus(
            Collection<UUID> uuids, ResourceStatus status);

    /**
     * Increments the persisted total view count for a resource.
     *
     * @param resourceId resource primary key
     * @return updated row count
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Resource r set r.viewCount = r.viewCount + 1 where r.id = :resourceId")
    int incrementViewCountById(@Param("resourceId") Long resourceId);
}
