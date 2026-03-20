package com.vocawik.repository.debate;

import com.vocawik.domain.debate.Debate;
import com.vocawik.domain.debate.DebateStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Debate} persistence access. */
public interface DebateRepository extends JpaRepository<Debate, Long> {

    @EntityGraph(attributePaths = {"actorUser", "actorGuest"})
    List<Debate> findAllByResourceIdAndIsDeletedFalseAndStatusNotOrderByCreatedAtDescIdDesc(
            Long resourceId, DebateStatus status);
}
