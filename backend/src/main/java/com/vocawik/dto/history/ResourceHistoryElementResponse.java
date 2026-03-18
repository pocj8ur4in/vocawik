package com.vocawik.dto.history;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary metadata for a resource history revision. */
public record ResourceHistoryElementResponse(
        UUID historyUuid,
        int revision,
        String actionType,
        HistoryActorResponse actor,
        LocalDateTime createdAt) {}
