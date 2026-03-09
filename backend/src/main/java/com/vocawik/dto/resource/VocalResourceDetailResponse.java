package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed vocal payload returned by resource detail endpoint. */
public record VocalResourceDetailResponse(
        UUID resourceUuid,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        String content,
        Object links,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ResourceNameDetailResponse> names,
        List<ResourceAclDetailResponse> acls,
        List<VocalSong> songs) {

    /** Creates an immutable vocal detail response. */
    public VocalResourceDetailResponse {
        names = List.copyOf(names);
        acls = List.copyOf(acls);
        songs = List.copyOf(songs);
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
