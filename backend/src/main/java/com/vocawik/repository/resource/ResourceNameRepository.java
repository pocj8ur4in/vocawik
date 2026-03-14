package com.vocawik.repository.resource;

import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link ResourceName} persistence access. */
public interface ResourceNameRepository extends JpaRepository<ResourceName, Long> {

    /** Finds all names by resource id in display order. */
    java.util.List<ResourceName> findAllByResourceIdOrderBySortOrderAscIdAsc(Long resourceId);

    /** Finds ranked resource-name candidates for query suggestions. */
    @Query(
            """
            select rn
            from ResourceName rn
            join fetch rn.resource r
            where r.isDeleted = false
              and r.status = :status
              and lower(rn.name) like concat('%', lower(:query), '%')
            order by
              case when lower(rn.name) like concat(lower(:query), '%') then 0 else 1 end,
              case when rn.isPrimary = true then 0 else 1 end,
              r.viewCount desc,
              r.updatedAt desc,
              rn.sortOrder asc,
              rn.id asc
            """)
    java.util.List<ResourceName> findSuggestionCandidates(
            @Param("status") ResourceStatus status,
            @Param("query") String query,
            Pageable pageable);

    /** Finds ranked vocal-name candidates for query suggestions. */
    @Query(
            """
            select rn
            from ResourceName rn
            where rn.resource.isDeleted = false
              and rn.resource.status = :status
              and lower(rn.name) like concat('%', lower(:query), '%')
              and exists (
                  select 1
                  from Vocal v
                  where v.resource = rn.resource
              )
            order by
              case when lower(rn.name) like concat(lower(:query), '%') then 0 else 1 end,
              case when rn.isPrimary = true then 0 else 1 end,
              rn.resource.viewCount desc,
              rn.resource.updatedAt desc,
              rn.sortOrder asc,
              rn.id asc
            """)
    java.util.List<ResourceName> findVocalSuggestionCandidates(
            @Param("status") ResourceStatus status,
            @Param("query") String query,
            Pageable pageable);

    /** Deletes all names by resource id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ResourceName rn where rn.resource.id = :resourceId")
    void deleteByResourceId(@Param("resourceId") Long resourceId);
}
