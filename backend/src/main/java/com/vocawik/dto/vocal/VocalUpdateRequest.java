package com.vocawik.dto.vocal;

import com.vocawik.common.i18n.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Request payload for vocal update. */
public record VocalUpdateRequest(
        @Valid CanonicalNameUpdateRequest canonicalName,
        String thumbnailUrl,
        String content,
        @Valid List<VocalLinkUpdateRequest> links,
        @Valid List<ResourceAliasUpdateRequest> aliases,
        @Valid List<ResourceAclUpdateRequest> acls,
        String captchaToken) {

    /** Defensive copy for mutable request fields while preserving null semantics. */
    public VocalUpdateRequest {
        links = links == null ? null : List.copyOf(links);
        aliases = aliases == null ? null : List.copyOf(aliases);
        acls = acls == null ? null : List.copyOf(acls);
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
    public record VocalLinkUpdateRequest(
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
}
