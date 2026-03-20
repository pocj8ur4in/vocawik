package com.vocawik.dto.debate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detail response payload for a single debate thread. */
public record DebateDetailResponse(
        UUID debateUuid,
        String title,
        String authorName,
        String status,
        LocalDateTime createdAt,
        Body body,
        List<Comment> comments) {

    public DebateDetailResponse {
        comments = List.copyOf(comments);
    }

    /** First comment used as the debate body. */
    public record Body(
            UUID commentUuid,
            String authorName,
            String content,
            int revision,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean isDeleted) {}

    /** Debate comment item including reply linkage. */
    public record Comment(
            UUID commentUuid,
            UUID parentCommentUuid,
            String authorName,
            String content,
            int revision,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean isDeleted) {}
}
