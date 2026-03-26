package com.vocawik.dto.vocadb;

import com.vocawik.common.i18n.Language;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Prefill payload resolved from a VocaDB link. */
public record VocadbPrefillResponse(
        boolean found,
        String sourceType,
        Long vocadbId,
        SongPrefill song,
        ArtistPrefill artist,
        VocalPrefill vocal) {

    public VocadbPrefillResponse {
        song = song == null ? null : song;
        artist = artist == null ? null : artist;
        vocal = vocal == null ? null : vocal;
    }

    /** Canonical name payload. */
    public record CanonicalNamePrefill(Language langCode, String name) {}

    /** Link payload aligned with create forms. */
    public record LinkPrefill(String type, String url, String content, boolean isDeleted) {}

    /** Song prefill payload. */
    public record SongPrefill(
            CanonicalNamePrefill canonicalName,
            String thumbnailUrl,
            LocalDateTime publishedAt,
            String songType,
            List<LinkPrefill> links,
            List<SongPvPrefill> pvs,
            List<SongArtistPrefill> artists,
            List<SongVocalPrefill> vocals,
            SongRelationPrefill relation) {

        public SongPrefill {
            links = links == null ? List.of() : List.copyOf(links);
            pvs = pvs == null ? List.of() : List.copyOf(pvs);
            artists = artists == null ? List.of() : List.copyOf(artists);
            vocals = vocals == null ? List.of() : List.copyOf(vocals);
        }
    }

    /** Song PV prefill payload. */
    public record SongPvPrefill(
            String service,
            String videoKey,
            String url,
            String title,
            String thumbnailUrl,
            Integer durationSeconds,
            LocalDateTime publishedAt,
            boolean isOfficial,
            Integer sortOrder) {}

    /** Song-artist prefill payload. */
    public record SongArtistPrefill(
            Long vocadbArtistId,
            UUID artistResourceUuid,
            String canonicalName,
            List<String> roles,
            boolean isMain,
            Integer sortOrder) {

        public SongArtistPrefill {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    /** Song-vocal prefill payload. */
    public record SongVocalPrefill(
            Long vocadbArtistId,
            UUID vocalResourceUuid,
            String canonicalName,
            boolean isMain,
            Integer sortOrder) {}

    /** Song relation prefill payload. */
    public record SongRelationPrefill(
            Long vocadbSongId, UUID songResourceUuid, String canonicalName) {}

    /** Artist prefill payload. */
    public record ArtistPrefill(
            CanonicalNamePrefill canonicalName,
            String thumbnailUrl,
            List<LinkPrefill> links,
            List<ArtistMemberPrefill> members) {

        public ArtistPrefill {
            links = links == null ? List.of() : List.copyOf(links);
            members = members == null ? List.of() : List.copyOf(members);
        }
    }

    /** Artist membership prefill payload. */
    public record ArtistMemberPrefill(
            Long vocadbArtistId,
            UUID groupArtistResourceUuid,
            String canonicalName,
            Integer sortOrder) {}

    /** Vocal prefill payload. */
    public record VocalPrefill(
            CanonicalNamePrefill canonicalName, String thumbnailUrl, List<LinkPrefill> links) {

        public VocalPrefill {
            links = links == null ? List.of() : List.copyOf(links);
        }
    }
}
