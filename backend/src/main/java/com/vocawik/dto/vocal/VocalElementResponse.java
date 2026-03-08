package com.vocawik.dto.vocal;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for vocal list responses. */
public record VocalElementResponse(
        UUID resourceUuid,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
