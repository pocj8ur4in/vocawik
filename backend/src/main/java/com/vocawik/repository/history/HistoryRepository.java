package com.vocawik.repository.history;

import com.vocawik.domain.history.History;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link History} persistence access. */
public interface HistoryRepository extends JpaRepository<History, Long> {}
