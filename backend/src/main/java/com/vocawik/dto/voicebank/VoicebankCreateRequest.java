package com.vocawik.dto.voicebank;

import com.vocawik.common.i18n.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Request payload for voicebank creation. */
public record VoicebankCreateRequest(
        @NotBlank @Size(max = 255) String canonicalName,
        String thumbnailUrl,
        String content,
        Object links,
        @NotNull UUID vocalCharacterResourceUuid,
        @NotBlank String voicebankType,
        @Valid List<ResourceNameCreateRequest> names,
        @Valid List<ResourceAclCreateRequest> acls) {

    /** Defensive copy for mutable request fields. */
    public VoicebankCreateRequest {
        names = names == null ? List.of() : List.copyOf(names);
        acls = acls == null ? List.of() : List.copyOf(acls);
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
}
