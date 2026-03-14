package com.vocawik.dto.artist;

import com.vocawik.common.i18n.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Request payload for artist update. */
public record ArtistUpdateRequest(
        @Valid CanonicalNameUpdateRequest canonicalName,
        String thumbnailUrl,
        String content,
        Object links,
        @Valid List<ResourceAliasUpdateRequest> aliases,
        @Valid List<ResourceAclUpdateRequest> acls,
        @Valid List<ArtistMemberUpdateRequest> members) {

    /** Defensive copy for mutable request fields while preserving null semantics. */
    public ArtistUpdateRequest {
        aliases = aliases == null ? null : List.copyOf(aliases);
        acls = acls == null ? null : List.copyOf(acls);
        members = members == null ? null : List.copyOf(members);
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

    /** Artist membership input where the updated artist is a member of group artists. */
    public record ArtistMemberUpdateRequest(
            @NotNull UUID groupArtistResourceUuid, @Min(0) Integer sortOrder) {}
}
