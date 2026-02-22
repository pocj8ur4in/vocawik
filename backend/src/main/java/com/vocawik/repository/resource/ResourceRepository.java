package com.vocawik.repository.resource;

import com.vocawik.domain.resource.Resource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Resource} persistence access. */
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    Optional<Resource> findByUuid(UUID uuid);

    /**
     * Increments view count.
     *
     * @param id resource id
     * @return updated row count
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Resource r SET r.viewCount = r.viewCount + 1 WHERE r.id = :id")
    int incrementViewCountById(@Param("id") Long id);
}
