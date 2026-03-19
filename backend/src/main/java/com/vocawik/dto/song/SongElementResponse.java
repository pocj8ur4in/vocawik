package com.vocawik.dto.song;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for song list responses. */
public record SongElementResponse(
        @Schema(description = "Song resource UUID") UUID resourceUuid,
        @Schema(description = "Canonical representative name", example = "Tell Your World")
                String canonicalName,
        @Schema(
                        description =
                                "Localized name matching the request language when available. "
                                        + "Null when no matching localized name exists.",
                        example = "텔 유어 월드",
                        nullable = true)
                String localizedName,
        @Schema(description = "Resource status", example = "ACTIVE") String status,
        @Schema(description = "Song type", example = "ORIGINAL") String songType,
        @Schema(description = "Total view count", example = "123") long viewCount,
        @Schema(description = "Thumbnail URL", nullable = true) String thumbnailUrl,
        @Schema(description = "Published timestamp", nullable = true) LocalDateTime publishedAt,
        @Schema(description = "Created timestamp", nullable = true) LocalDateTime createdAt,
        @Schema(description = "Updated timestamp", nullable = true) LocalDateTime updatedAt) {}
