package com.vocawik.dto.history;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for the global recent changes feed. */
public record RecentChangeElementResponse(
        LocalDateTime createdAt,
        UUID resourceUuid,
        String canonicalName,
        String localizedName,
        String resourceType,
        String actionType,
        String actorUserNickname) {}
