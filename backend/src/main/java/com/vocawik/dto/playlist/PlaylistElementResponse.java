package com.vocawik.dto.playlist;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for playlist list responses. */
public record PlaylistElementResponse(
        UUID resourceUuid,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
