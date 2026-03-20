package com.vocawik.dto.debate;

import java.time.LocalDateTime;
import java.util.UUID;

/** Response payload for a single debate comment. */
public record DebateCommentResponse(
        UUID commentUuid,
        UUID parentCommentUuid,
        String authorName,
        String content,
        int revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean isDeleted) {}
