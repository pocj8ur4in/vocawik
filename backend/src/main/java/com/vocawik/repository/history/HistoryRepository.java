package com.vocawik.repository.history;

import com.vocawik.domain.history.History;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link History} persistence access. */
public interface HistoryRepository extends JpaRepository<History, Long> {

    /** Finds a history row by UUID. */
    Optional<History> findByUuid(UUID uuid);

    /** Finds resource histories in reverse revision order. */
    Page<History> findAllByResourceIdOrderByRevisionDescCreatedAtDesc(
            Long resourceId, Pageable pageable);

    /** Finds resource histories in reverse revision order. */
    List<History> findAllByResourceIdOrderByRevisionDescCreatedAtDesc(Long resourceId);
}
