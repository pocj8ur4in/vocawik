package com.vocawik.repository.debate;

import com.vocawik.domain.debate.Debate;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Debate} persistence access. */
public interface DebateRepository extends JpaRepository<Debate, Long> {}
