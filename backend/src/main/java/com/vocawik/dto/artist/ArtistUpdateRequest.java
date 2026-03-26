package com.vocawik.dto.artist;

import com.vocawik.common.i18n.Language;
import com.vocawik.domain.resource.ResourceStatus;
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
        @NotNull @Valid CanonicalNameUpdateRequest canonicalName,
        String thumbnailUrl,
        String content,
        @Valid List<ArtistLinkUpdateRequest> links,
        @Valid List<ResourceAliasUpdateRequest> aliases,
        @Valid List<ResourceAclUpdateRequest> acls,
        @Valid List<ArtistMemberUpdateRequest> members,
        ResourceStatus status,
        Boolean isDeleted,
        String captchaToken) {

    /** Defensive copy for mutable request fields while preserving null semantics. */
    public ArtistUpdateRequest {
        links = links == null ? List.of() : List.copyOf(links);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        acls = acls == null ? List.of() : List.copyOf(acls);
        members = members == null ? List.of() : List.copyOf(members);
    }

    /** Canonical resource name input. */
    public record CanonicalNameUpdateRequest(
            @NotNull Language langCode, @NotBlank @Size(max = 255) String name) {}

    /** Alias resource name input. */
    public record ResourceAliasUpdateRequest(
            @NotNull Language langCode,
            @NotBlank @Size(max = 255) String name,
            @Min(0) Integer sortOrder) {}

    /** External link input. */
    public record ArtistLinkUpdateRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 2048) String url,
            String content,
            boolean isDeleted) {}

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
