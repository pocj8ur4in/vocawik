package com.vocawik.repository.debate;

import com.vocawik.domain.debate.DebateComment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link DebateComment} persistence access. */
public interface DebateCommentRepository extends JpaRepository<DebateComment, Long> {

    @Query(
            """
            select c.debate.id as debateId, count(c.id) as commentCount
            from DebateComment c
            where c.debate.id in :debateIds
              and c.isDeleted = false
            group by c.debate.id
            """)
    List<DebateCommentCountProjection> countActiveCommentsByDebateIds(
            @Param("debateIds") Collection<Long> debateIds);

    @EntityGraph(attributePaths = {"actorUser", "actorGuest", "parentComment"})
    List<DebateComment> findAllByDebateIdOrderByCreatedAtAscIdAsc(Long debateId);
}
