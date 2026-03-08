package com.vocawik.dto.voicebank;

import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for voicebank list responses. */
public record VoicebankElementResponse(
        UUID resourceUuid,
        String canonicalName,
        String status,
        String voicebankType,
        long viewCount,
        String thumbnailUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
