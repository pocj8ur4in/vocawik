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
                r.canonicalName as canonicalName,
                h.actionType as actionType,
                u.nickname as actorUserNickname
            from History h
            join h.resource r
            left join h.actorUser u
            order by h.createdAt desc, h.id desc
            """)
    List<RecentChangeProjection> findRecentChanges(Pageable pageable);
}
