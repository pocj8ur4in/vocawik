package com.vocawik.repository.history;

import com.vocawik.domain.history.History;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for {@link History} persistence access. */
public interface HistoryRepository extends JpaRepository<History, Long> {

    long countByActorUserId(Long actorUserId);

    /** Counts distinct user contributors captured in histories. */
    @Query("select count(distinct h.actorUser.id) from History h where h.actorUser is not null")
    long countDistinctActorUsers();

    /** Counts distinct guest contributors captured in histories. */
    @Query("select count(distinct h.actorGuest.id) from History h where h.actorGuest is not null")
    long countDistinctActorGuests();

    /** Finds a history row by UUID. */
    Optional<History> findByUuid(UUID uuid);

    /** Finds resource histories in reverse revision order. */
    Page<History> findAllByResourceIdOrderByRevisionDescCreatedAtDesc(
            Long resourceId, Pageable pageable);

    /** Finds resource histories in reverse revision order. */
    List<History> findAllByResourceIdOrderByRevisionDescCreatedAtDesc(Long resourceId);

    /** Finds recent change rows in reverse creation order. */
    @Query(
            """
            select
                h.createdAt as createdAt,
                r.id as resourceId,
                r.uuid as resourceUuid,
                r.canonicalName as canonicalName,
                r.resourceType as resourceType,
                h.actionType as actionType,
                u.nickname as actorUserNickname
            from History h
            join h.resource r
            left join h.actorUser u
            order by h.createdAt desc, h.id desc
            """)
    List<RecentChangeProjection> findRecentChanges(Pageable pageable);

    /** Finds recent visible change rows for public clients in reverse creation order. */
    @Query(
            """
            select
                h.createdAt as createdAt,
                r.id as resourceId,
                r.uuid as resourceUuid,
                r.canonicalName as canonicalName,
                r.resourceType as resourceType,
                h.actionType as actionType,
                u.nickname as actorUserNickname
            from History h
            join h.resource r
            left join h.actorUser u
            where r.isDeleted = false
              and r.status = com.vocawik.domain.resource.ResourceStatus.ACTIVE
            order by h.createdAt desc, h.id desc
            """)
    List<RecentChangeProjection> findRecentVisibleChanges(Pageable pageable);
}
