package com.vocawik.dto.history;

import java.util.UUID;

/** Actor summary for history timeline/detail payloads. */
public record HistoryActorResponse(
        String type, UUID userUuid, String userNickname, UUID guestUuid) {}
