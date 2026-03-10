package com.vocawik.dto.history;

import java.time.LocalDateTime;
import java.util.UUID;

/** Detail payload for a single resource history revision. */
public record ResourceHistoryDetailResponse(
        UUID historyUuid,
        UUID resourceUuid,
        int revision,
        int baseRevision,
        String actionType,
        UUID actorUserUuid,
        UUID actorGuestUuid,
        String contentHash,
        LocalDateTime createdAt,
        Object snapshotData) {}
