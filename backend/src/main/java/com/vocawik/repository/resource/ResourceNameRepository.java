package com.vocawik.repository.resource;

import com.vocawik.domain.resource.ResourceName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link ResourceName} persistence access. */
public interface ResourceNameRepository extends JpaRepository<ResourceName, Long> {

    /** Finds all names by resource id in display order. */
    java.util.List<ResourceName> findAllByResourceIdOrderBySortOrderAscIdAsc(Long resourceId);

    /** Deletes all names by resource id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ResourceName rn where rn.resource.id = :resourceId")
    void deleteByResourceId(@Param("resourceId") Long resourceId);
}
