package com.vocawik.dto.song;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for song list responses. */
public record SongElementResponse(
        UUID resourceUuid,
        String canonicalName,
        String status,
        String songType,
        long viewCount,
        String thumbnailUrl,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
