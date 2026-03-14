package com.vocawik.dto.playlist;

import com.vocawik.common.i18n.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Request payload for playlist update. */
public record PlaylistUpdateRequest(
        @Valid CanonicalNameUpdateRequest canonicalName,
        String thumbnailUrl,
        String content,
        Boolean isPublic,
        @Valid List<ResourceAliasUpdateRequest> aliases,
        @Valid List<ResourceAclUpdateRequest> acls,
        @Valid List<PlaylistSongUpdateRequest> songs) {

    /** Defensive copy for mutable request fields while preserving null semantics. */
    public PlaylistUpdateRequest {
        aliases = aliases == null ? null : List.copyOf(aliases);
        acls = acls == null ? null : List.copyOf(acls);
        songs = songs == null ? null : List.copyOf(songs);
    }

    /** Canonical resource name input. */
    public record CanonicalNameUpdateRequest(
            @NotNull Language langCode, @NotBlank @Size(max = 255) String name) {}

    /** Alias resource name input. */
    public record ResourceAliasUpdateRequest(
            @NotNull Language langCode,
            @NotBlank @Size(max = 255) String name,
            @Min(0) Integer sortOrder) {}

    /** ACL rule input. */
    public record ResourceAclUpdateRequest(
            @NotBlank String action,
            @NotBlank String subjectType,
            String subjectValue,
            String effect,
            @Min(0) Integer priority,
            LocalDateTime expiresAt) {}

    /** Playlist song input. */
    public record PlaylistSongUpdateRequest(
            @NotNull UUID songResourceUuid, @NotNull @Min(0) Integer sortOrder) {}
}
