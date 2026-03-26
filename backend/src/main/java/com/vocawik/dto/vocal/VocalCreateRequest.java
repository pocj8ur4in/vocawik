package com.vocawik.dto.vocal;

import com.vocawik.common.i18n.Language;
import com.vocawik.domain.resource.ResourceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Request payload for vocal creation. */
public record VocalCreateRequest(
        @NotNull @Valid CanonicalNameCreateRequest canonicalName,
        String thumbnailUrl,
        String content,
        @Valid List<VocalLinkCreateRequest> links,
        @Valid List<ResourceAliasCreateRequest> aliases,
        @Valid List<ResourceAclCreateRequest> acls,
        ResourceStatus status,
        Boolean isDeleted,
        String captchaToken) {

    /** Defensive copy for mutable request fields. */
    public VocalCreateRequest {
        links = links == null ? List.of() : List.copyOf(links);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        acls = acls == null ? List.of() : List.copyOf(acls);
    }

    /** Canonical resource name input. */
    public record CanonicalNameCreateRequest(
            @NotNull Language langCode, @NotBlank @Size(max = 255) String name) {}

    /** Alias resource name input. */
    public record ResourceAliasCreateRequest(
            @NotNull Language langCode,
            @NotBlank @Size(max = 255) String name,
            @Min(0) Integer sortOrder) {}

    /** External link input. */
    public record VocalLinkCreateRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 2048) String url,
            String content,
            boolean isDeleted) {}

    /** ACL rule input. */
    public record ResourceAclCreateRequest(
            @NotBlank String action,
            @NotBlank String subjectType,
            String subjectValue,
            String effect,
            @Min(0) Integer priority,
            LocalDateTime expiresAt) {}
}
