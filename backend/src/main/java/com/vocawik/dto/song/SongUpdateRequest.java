package com.vocawik.dto.song;

import com.vocawik.common.i18n.Language;
import com.vocawik.domain.resource.ResourceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request payload for song update. */
public record SongUpdateRequest(
        @NotNull @Valid CanonicalNameUpdateRequest canonicalName,
        String thumbnailUrl,
        String content,
        @Valid List<SongLinkUpdateRequest> links,
        LocalDateTime publishedAt,
        @NotBlank String songType,
        @Valid List<ResourceAliasUpdateRequest> aliases,
        @Valid List<ResourceAclUpdateRequest> acls,
        @Valid List<SongLyricUpdateRequest> lyrics,
        @Valid List<SongPvUpdateRequest> pvs,
        @Valid List<SongArtistUpdateRequest> artists,
        @Valid List<SongVocalUpdateRequest> vocals,
        UUID relationsTargetSongResourceUuid,
        ResourceStatus status,
        Boolean isDeleted,
        String captchaToken) {

    /** Defensive copy for mutable request fields while preserving null semantics. */
    public SongUpdateRequest {
        links = links == null ? List.of() : List.copyOf(links);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        acls = acls == null ? List.of() : List.copyOf(acls);
        lyrics = lyrics == null ? List.of() : List.copyOf(lyrics);
        pvs = pvs == null ? List.of() : List.copyOf(pvs);
        artists = artists == null ? List.of() : List.copyOf(artists);
        vocals = vocals == null ? List.of() : List.copyOf(vocals);
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
    public record SongLinkUpdateRequest(
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

    /** Song lyric input. */
    public record SongLyricUpdateRequest(
            @NotEmpty Set<Language> langCodes,
            @NotNull Object lyrics,
            boolean isPrimary,
            @Min(0) Integer sortOrder) {

        /** Defensive copy for mutable request fields. */
        public SongLyricUpdateRequest {
            langCodes = langCodes == null ? Set.of() : Set.copyOf(langCodes);
        }
    }

    /** Song PV input. */
    public record SongPvUpdateRequest(
            @NotBlank String service,
            @NotBlank String videoKey,
            @NotBlank @Size(max = 2048) String url,
            @Size(max = 255) String title,
            String thumbnailUrl,
            String uploaderKey,
            @Min(0) Integer durationSeconds,
            boolean isOfficial,
            LocalDateTime publishedAt,
            @Valid SongPvExtraUpdateRequest extra,
            @Min(0) Integer sortOrder) {}

    /** Song PV provider-specific extra metadata input. */
    public record SongPvExtraUpdateRequest(
            @Size(max = 2048) String audioUrl,
            @Min(0) Long cid,
            @Size(max = 2048) String externalUrl) {}

    /** Song-artist mapping input. */
    public record SongArtistUpdateRequest(
            @NotNull UUID artistResourceUuid,
            @NotEmpty Set<@NotBlank String> roles,
            boolean isMain,
            @Min(0) Integer sortOrder) {

        /** Defensive copy for mutable request fields. */
        public SongArtistUpdateRequest {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }

    /** Song-vocal mapping input. */
    public record SongVocalUpdateRequest(
            @NotNull UUID vocalResourceUuid, boolean isMain, @Min(0) Integer sortOrder) {}
}
