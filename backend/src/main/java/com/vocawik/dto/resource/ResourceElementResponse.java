package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for resource list responses. */
public record ResourceElementResponse(
        UUID uuid,
        String canonicalName,
        String resourceType,
        String status,
        long viewCount,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
