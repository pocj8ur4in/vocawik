package com.vocawik.repository.resource;

import com.vocawik.domain.resource.Resource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Resource} persistence access. */
public interface ResourceRepository
        extends JpaRepository<Resource, Long>, ResourceCriteriaRepository {

    /**
     * Finds an active resource by UUID.
     *
     * @param uuid resource UUID
     * @return active resource when found
     */
    Optional<Resource> findByUuidAndIsDeletedFalse(UUID uuid);
}
