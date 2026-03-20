package com.vocawik.dto.debate;

import java.util.UUID;

/** Response payload for a debate status change. */
public record DebateStatusResponse(UUID debateUuid, String status) {}
