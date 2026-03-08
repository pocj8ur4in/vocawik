package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resource name item for detail payloads. */
public record ResourceNameDetailResponse(
        UUID nameUuid,
        String langCode,
        String name,
        boolean isPrimary,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
