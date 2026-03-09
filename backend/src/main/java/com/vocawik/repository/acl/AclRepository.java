package com.vocawik.repository.acl;

import com.vocawik.domain.acl.Acl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link Acl} persistence access. */
public interface AclRepository extends JpaRepository<Acl, Long> {

    /** Finds all ACL rows by resource id in evaluation order. */
    java.util.List<Acl> findAllByResourceIdOrderByPriorityAscIdAsc(Long resourceId);

    /** Deletes all ACL rows by resource id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from Acl acl where acl.resource.id = :resourceId")
    void deleteByResourceId(@Param("resourceId") Long resourceId);
}
