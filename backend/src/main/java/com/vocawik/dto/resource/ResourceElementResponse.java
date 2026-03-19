package com.vocawik.dto.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

/** Summary item for resource list responses. */
public record ResourceElementResponse(
        @Schema(description = "Resource UUID") UUID uuid,
        @Schema(description = "Canonical representative name", example = "Hatsune Miku")
                String canonicalName,
        @Schema(
                        description =
                                "Localized name matching the request language when available. "
                                        + "Null when no matching localized name exists.",
                        example = "初音ミク",
                        nullable = true)
                String localizedName,
        @Schema(description = "Resource type", example = "VOCAL") String resourceType,
        @Schema(description = "Resource status", example = "ACTIVE") String status,
        @Schema(description = "Total view count", example = "123") long viewCount,
        @Schema(description = "Thumbnail URL", nullable = true) String thumbnailUrl,
        @Schema(description = "Created timestamp", nullable = true) LocalDateTime createdAt,
        @Schema(description = "Updated timestamp", nullable = true) LocalDateTime updatedAt) {}
