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

/** Request payload for playlist creation. */
public record PlaylistCreateRequest(
        @NotNull @Valid CanonicalNameCreateRequest canonicalName,
        String thumbnailUrl,
        String content,
        Boolean isPublic,
        @Valid List<ResourceAliasCreateRequest> aliases,
        @Valid List<ResourceAclCreateRequest> acls,
        @Valid List<PlaylistSongCreateRequest> songs,
        String captchaToken) {

    /** Defensive copy for mutable request fields. */
    public PlaylistCreateRequest {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        acls = acls == null ? List.of() : List.copyOf(acls);
        songs = songs == null ? List.of() : List.copyOf(songs);
    }

    /** Canonical resource name input. */
    public record CanonicalNameCreateRequest(
            @NotNull Language langCode, @NotBlank @Size(max = 255) String name) {}

    /** Alias resource name input. */
    public record ResourceAliasCreateRequest(
            @NotNull Language langCode,
            @NotBlank @Size(max = 255) String name,
            @Min(0) Integer sortOrder) {}

    /** ACL rule input. */
    public record ResourceAclCreateRequest(
            @NotBlank String action,
            @NotBlank String subjectType,
            String subjectValue,
            String effect,
            @Min(0) Integer priority,
            LocalDateTime expiresAt) {}

    /** Playlist-song mapping input. */
    public record PlaylistSongCreateRequest(
            @NotNull UUID songResourceUuid, @NotNull @Min(0) Integer sortOrder) {}
}
