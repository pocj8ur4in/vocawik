package com.vocawik.dto.history;

import java.time.LocalDateTime;
import java.util.UUID;

/** Detail payload for a single resource history revision. */
public record ResourceHistoryDetailResponse(
        UUID historyUuid,
        int revision,
        int baseRevision,
        String actionType,
        HistoryActorResponse actor,
        LocalDateTime createdAt,
        Object snapshotData) {}
