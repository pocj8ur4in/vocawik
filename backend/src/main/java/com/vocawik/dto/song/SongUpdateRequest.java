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

/** Request payload for song update. */
public record SongUpdateRequest(
        String canonicalName,
        String thumbnailUrl,
        String content,
        Object links,
        LocalDateTime publishedAt,
        String songType,
        @Valid List<ResourceNameUpdateRequest> names,
        @Valid List<ResourceAclUpdateRequest> acls,
        @Valid List<SongLyricUpdateRequest> lyrics,
        @Valid List<SongPvUpdateRequest> pvs,
        @Valid List<SongArtistUpdateRequest> artists,
        @Valid List<SongVocalUpdateRequest> vocals,
        @Valid List<SongVoicebankUpdateRequest> voicebanks,
        @Valid List<SongRelationUpdateRequest> relations) {

    /** Defensive copy for mutable request fields while preserving null semantics. */
    public SongUpdateRequest {
        names = names == null ? null : List.copyOf(names);
        acls = acls == null ? null : List.copyOf(acls);
        lyrics = lyrics == null ? null : List.copyOf(lyrics);
        pvs = pvs == null ? null : List.copyOf(pvs);
        artists = artists == null ? null : List.copyOf(artists);
        vocals = vocals == null ? null : List.copyOf(vocals);
        voicebanks = voicebanks == null ? null : List.copyOf(voicebanks);
        relations = relations == null ? null : List.copyOf(relations);
    }

    /** Localized resource name input. */
    public record ResourceNameUpdateRequest(
            @NotNull Language langCode,
            @NotBlank @Size(max = 255) String name,
            boolean isPrimary,
            @Min(0) Integer sortOrder) {}

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
            @NotBlank @Size(max = 100) String videoKey,
            @Size(max = 255) String title,
            String thumbnailUrl,
            @Size(max = 100) String uploaderKey,
            @Min(0) Integer durationSeconds,
            boolean isOfficial,
            LocalDateTime publishedAt,
            @Min(0) Integer sortOrder) {}

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

    /** Song-voicebank mapping input. */
    public record SongVoicebankUpdateRequest(
            @NotNull UUID voicebankResourceUuid, boolean isMain, @Min(0) Integer sortOrder) {}

    /** Song relation input. */
    public record SongRelationUpdateRequest(@NotNull UUID targetSongResourceUuid) {}
}
