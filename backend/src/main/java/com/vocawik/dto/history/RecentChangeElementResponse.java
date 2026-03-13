package com.vocawik.dto.history;

import java.time.LocalDateTime;

/** Summary item for the global recent changes feed. */
public record RecentChangeElementResponse(
        LocalDateTime createdAt,
        String canonicalName,
        String actionType,
        String actorUserNickname) {}
