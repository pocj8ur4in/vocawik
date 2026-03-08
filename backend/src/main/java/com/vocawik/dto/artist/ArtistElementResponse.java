package com.vocawik.dto.artist;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for artist list responses. */
public record ArtistElementResponse(
        UUID resourceUuid,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
