package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed vocal payload returned by resource detail endpoint. */
public record VocalResourceDetailResponse(
        UUID resourceUuid,
        boolean isDeleted,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        String content,
        List<VocalLink> links,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ResourceNameDetailResponse> names,
        List<ResourceAclDetailResponse> acls,
        VocalSongs songs) {

    /** Creates an immutable vocal detail response. */
    public VocalResourceDetailResponse {
        links = List.copyOf(links);
        names = List.copyOf(names);
        acls = List.copyOf(acls);
    }

    /** Link item for the vocal detail payload. */
    public record VocalLink(String type, String url, String content, boolean isDeleted) {}

    /** Aggregated song section for vocal detail payload. */
    public record VocalSongs(
            long count, List<VocalSong> recentSongs, List<VocalSong> popularSongs) {
        public VocalSongs {
            recentSongs = List.copyOf(recentSongs);
            popularSongs = List.copyOf(popularSongs);
        }
    }

    /** Song mapping item for the vocal detail payload. */
    public record VocalSong(
            UUID songResourceUuid,
            String songCanonicalName,
            String songThumbnailUrl,
            String songType,
            LocalDateTime publishedAt,
            boolean isMain,
            int sortOrder) {}
}
