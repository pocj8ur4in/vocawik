package com.vocawik.dto.artist;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for artist list responses. */
public record ArtistElementResponse(
        @Schema(description = "Artist resource UUID") UUID resourceUuid,
        @Schema(description = "Canonical representative name", example = "Hachioji-P")
                String canonicalName,
        @Schema(
                        description =
                                "Localized name matching the request language when available. "
                                        + "Null when no matching localized name exists.",
                        example = "八王子P",
                        nullable = true)
                String localizedName,
        @Schema(description = "Resource status", example = "ACTIVE") String status,
        @Schema(description = "Whether the resource is soft-deleted", example = "false")
                boolean isDeleted,
        @Schema(description = "Total view count", example = "123") long viewCount,
        @Schema(description = "Thumbnail URL", nullable = true) String thumbnailUrl,
        @Schema(description = "Created timestamp", nullable = true) LocalDateTime createdAt,
        @Schema(description = "Updated timestamp", nullable = true) LocalDateTime updatedAt) {}
