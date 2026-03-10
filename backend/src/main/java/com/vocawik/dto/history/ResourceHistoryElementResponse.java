package com.vocawik.dto.history;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary metadata for a resource history revision. */
public record ResourceHistoryElementResponse(
        UUID historyUuid,
        UUID resourceUuid,
        int revision,
        int baseRevision,
        String actionType,
        UUID actorUserUuid,
        UUID actorGuestUuid,
        String contentHash,
        LocalDateTime createdAt) {}
