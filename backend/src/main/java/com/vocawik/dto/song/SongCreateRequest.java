package com.vocawik.dto.song;

import com.vocawik.common.i18n.Language;
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

/** Request payload for song creation. */
public record SongCreateRequest(
        @NotNull @Valid CanonicalNameCreateRequest canonicalName,
        String thumbnailUrl,
        String content,
        Object links,
        LocalDateTime publishedAt,
        @NotBlank String songType,
        @Valid List<ResourceAliasCreateRequest> aliases,
        @Valid List<ResourceAclCreateRequest> acls,
        @Valid List<SongLyricCreateRequest> lyrics,
        @Valid List<SongPvCreateRequest> pvs,
        @Valid List<SongArtistCreateRequest> artists,
        @Valid List<SongVocalCreateRequest> vocals,
        UUID relationsTargetSongResourceUuid) {

    /** Defensive copy for mutable request fields. */
    public SongCreateRequest {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        acls = acls == null ? List.of() : List.copyOf(acls);
        lyrics = lyrics == null ? List.of() : List.copyOf(lyrics);
        pvs = pvs == null ? List.of() : List.copyOf(pvs);
        artists = artists == null ? List.of() : List.copyOf(artists);
        vocals = vocals == null ? List.of() : List.copyOf(vocals);
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

    /** Song lyric input. */
    public record SongLyricCreateRequest(
            @NotEmpty Set<Language> langCodes,
            @NotNull Object lyrics,
            boolean isPrimary,
            @Min(0) Integer sortOrder) {

        /** Defensive copy for mutable request fields. */
        public SongLyricCreateRequest {
            langCodes = langCodes == null ? Set.of() : Set.copyOf(langCodes);
        }
    }

    /** Song PV input. */
    public record SongPvCreateRequest(
            @NotBlank String service,
            @NotBlank @Size(max = 100) String videoKey,
            @Size(max = 255) String title,
            String thumbnailUrl,
            @Size(max = 100) String uploaderKey,
            @Min(0) Integer durationSeconds,
            boolean isOfficial,
            LocalDateTime publishedAt,
            @Min(0) Integer sortOrder) {}

    /** Song-artist mapping input. */
    public record SongArtistCreateRequest(
            @NotNull UUID artistResourceUuid,
            @NotEmpty Set<@NotBlank String> roles,
            boolean isMain,
            @Min(0) Integer sortOrder) {

        /** Defensive copy for mutable request fields. */
        public SongArtistCreateRequest {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }

    /** Song-vocal mapping input. */
    public record SongVocalCreateRequest(
            @NotNull UUID vocalResourceUuid, boolean isMain, @Min(0) Integer sortOrder) {}
}
