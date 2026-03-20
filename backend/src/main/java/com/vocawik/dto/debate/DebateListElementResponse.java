package com.vocawik.dto.debate;

import java.time.LocalDateTime;
import java.util.UUID;

/** Debate list item for resource discussion listings. */
public record DebateListElementResponse(
        UUID debateUuid,
        String title,
        String authorName,
        String status,
        LocalDateTime createdAt,
        long commentCount) {}
