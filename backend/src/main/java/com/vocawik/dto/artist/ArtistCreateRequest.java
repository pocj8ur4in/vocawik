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

/** Request payload for artist creation. */
public record ArtistCreateRequest(
        @NotBlank @Size(max = 255) String canonicalName,
        String thumbnailUrl,
        String content,
        Object links,
        @Valid List<ResourceNameCreateRequest> names,
        @Valid List<ResourceAclCreateRequest> acls,
        @Valid List<ArtistGroupCreateRequest> groups) {

    /** Defensive copy for mutable request fields. */
    public ArtistCreateRequest {
        names = names == null ? List.of() : List.copyOf(names);
        acls = acls == null ? List.of() : List.copyOf(acls);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /** Localized resource name input. */
    public record ResourceNameCreateRequest(
            @NotNull Language langCode,
            @NotBlank @Size(max = 255) String name,
            boolean isPrimary,
            @Min(0) Integer sortOrder) {}

    /** ACL rule input. */
    public record ResourceAclCreateRequest(
            @NotBlank String action,
            @NotBlank String subjectType,
            String subjectValue,
            String effect,
            @Min(0) Integer priority,
            LocalDateTime expiresAt) {}

    /** Artist-group mapping input where the created artist is the group owner. */
    public record ArtistGroupCreateRequest(
            @NotNull UUID memberArtistResourceUuid, @Min(0) Integer sortOrder) {}
}
