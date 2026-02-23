package com.vocawik.repository.debate;

import com.vocawik.domain.debate.DebateComment;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link DebateComment} persistence access. */
public interface DebateCommentRepository extends JpaRepository<DebateComment, Long> {}
